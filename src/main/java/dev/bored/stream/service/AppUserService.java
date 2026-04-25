package dev.bored.stream.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.bored.stream.entity.AppUser;
import dev.bored.stream.repository.AppUserRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the local {@code app_user} mirror in sync with the Supabase JWT.
 *
 * <p>Every authenticated request that reaches the dispatcher servlet is
 * routed through {@code JwtUserSyncInterceptor}, which calls
 * {@link #upsertFromJwt(Jwt)} below. The first time a user shows up we
 * insert a row keyed by their JWT {@code sub}; subsequent calls refresh
 * the cached display name and avatar URL from the latest claims.</p>
 *
 * <p><b>Why Caffeine in front:</b> the upsert is cheap individually, but
 * doing it on every single authenticated request would mean a write
 * round-trip to Postgres on every comment, like, and post-fetch. The
 * in-process Caffeine cache records "we synced this user in the last
 * hour" and short-circuits the DB write when the entry is fresh. JVM
 * memory only — each Cloud Run instance has its own copy, so a user
 * could be re-synced up to N times across N instances on a cold burst,
 * which is fine; the upsert is idempotent.</p>
 *
 * <p><b>Race safety:</b> two concurrent requests from a brand-new user
 * can both observe the user as missing and both try to INSERT. One wins,
 * the other gets a {@link DataIntegrityViolationException} on the PK.
 * We catch it and re-fetch — by that point the row exists.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-25
 */
@Service
@AllArgsConstructor
public class AppUserService {

    private static final Logger log = LoggerFactory.getLogger(AppUserService.class);

    /** Upper bound on numeric suffixes when generating a unique handle. */
    private static final int MAX_HANDLE_SUFFIX = 1000;

    /** How long to skip the upsert for a user we just synced. */
    private static final Duration RECENT_SYNC_TTL = Duration.ofHours(1);

    /** Max chars a handle base can occupy, leaves room for a numeric suffix. */
    private static final int MAX_HANDLE_BASE_LENGTH = 30;

    private final AppUserRepository appUserRepository;

    /**
     * Tracks the last time we synced each user. Caffeine handles eviction;
     * we never explicitly remove. If a user's claims change inside the
     * 1-hour window, the change is just delayed — eventual consistency
     * is fine for display name / avatar.
     */
    private final Cache<UUID, Instant> recentSyncs = Caffeine.newBuilder()
            .expireAfterWrite(RECENT_SYNC_TTL)
            .maximumSize(10_000)
            .build();

    /**
     * Inserts or updates the local {@code app_user} row corresponding to a
     * Supabase JWT.
     *
     * @param jwt the authenticated Supabase JWT — must have a valid {@code sub}
     * @return the persisted (or already-up-to-date) {@link AppUser}
     */
    @Transactional
    public AppUser upsertFromJwt(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());

        // Fast path: synced recently, the row must exist. If it doesn't
        // (cache says fresh but row is missing — only happens after a manual
        // DB wipe), fall through to the slow path.
        if (recentSyncs.getIfPresent(userId) != null) {
            Optional<AppUser> cached = appUserRepository.findById(userId);
            if (cached.isPresent()) {
                return cached.get();
            }
            log.warn("Recent-sync cache hit for {} but no DB row — re-syncing", userId);
            recentSyncs.invalidate(userId);
        }

        String displayName = resolveDisplayName(jwt);
        String avatarUrl = resolveAvatarUrl(jwt);

        AppUser saved;
        Optional<AppUser> existing = appUserRepository.findById(userId);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            // Handles are sticky — never mutate after first insert. They
            // appear in URLs and UI; changing them retroactively breaks
            // links and identity.
            user.setDisplayName(displayName);
            user.setAvatarUrl(avatarUrl);
            saved = appUserRepository.save(user);
        } else {
            saved = createNewUser(jwt, userId, displayName, avatarUrl);
        }

        recentSyncs.put(userId, Instant.now());
        return saved;
    }

    /**
     * First-write insertion path. Generates a unique handle, persists,
     * and recovers from the rare race where another request inserted
     * the same {@code sub} a moment earlier.
     */
    private AppUser createNewUser(Jwt jwt, UUID userId, String displayName, String avatarUrl) {
        AppUser user = new AppUser();
        user.setUserId(userId);
        user.setDisplayName(displayName);
        user.setAvatarUrl(avatarUrl);
        user.setHandle(generateUniqueHandle(jwt, userId));
        try {
            return appUserRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Race: another request beat us to the insert. Whatever they
            // wrote is fine — re-fetch and return that.
            log.debug("Concurrent insert for {} — recovering by re-fetch", userId);
            return appUserRepository.findById(userId).orElseThrow(() -> e);
        }
    }

    /**
     * Picks a display name from the JWT, with a fallback chain:
     * <ol>
     *   <li>top-level {@code name} claim</li>
     *   <li>{@code user_metadata.full_name}</li>
     *   <li>local-part of {@code email}</li>
     *   <li>{@code "User <first 8 chars of UUID>"}</li>
     * </ol>
     */
    String resolveDisplayName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (isNonBlank(name)) return trim(name, 100);

        String fullName = readNestedClaim(jwt, "user_metadata", "full_name");
        if (isNonBlank(fullName)) return trim(fullName, 100);

        String email = jwt.getClaimAsString("email");
        if (isNonBlank(email)) {
            String local = emailLocalPart(email);
            if (isNonBlank(local)) return trim(local, 100);
        }

        return "User " + jwt.getSubject().substring(0, 8);
    }

    /**
     * Returns the local part of an email — chars before the first '@'.
     * Returns {@code ""} if the email starts with '@' (no local part);
     * returns the whole string if no '@' is present (treat as a bare
     * handle).
     */
    private static String emailLocalPart(String email) {
        int at = email.indexOf('@');
        if (at > 0) return email.substring(0, at);
        if (at < 0) return email;
        return "";
    }

    /**
     * Picks an avatar URL from the JWT, with a fallback chain. Returns
     * {@code null} if no claim provided one — that's a valid state.
     */
    String resolveAvatarUrl(Jwt jwt) {
        String picture = jwt.getClaimAsString("picture");
        if (isNonBlank(picture)) return trim(picture, 500);

        String metaAvatar = readNestedClaim(jwt, "user_metadata", "avatar_url");
        if (isNonBlank(metaAvatar)) return trim(metaAvatar, 500);

        return null;
    }

    /**
     * Generates a handle that doesn't collide with any existing one.
     *
     * <p>Algorithm: derive a base from email-local-part or display name,
     * sanitize to {@code [a-z0-9-]}, look up; if taken, append numeric
     * suffix and retry up to {@link #MAX_HANDLE_SUFFIX} times. Beyond
     * that we fall back to a UUID-prefixed handle which is guaranteed
     * to be at most as colliding as the UUID itself.</p>
     */
    String generateUniqueHandle(Jwt jwt, UUID userId) {
        String base = sanitizeHandle(deriveBaseHandle(jwt));
        if (base.isEmpty()) {
            base = "u" + userId.toString().substring(0, 8);
        }
        if (!appUserRepository.existsByHandle(base)) {
            return base;
        }
        for (int i = 1; i <= MAX_HANDLE_SUFFIX; i++) {
            String candidate = base + i;
            if (!appUserRepository.existsByHandle(candidate)) {
                return candidate;
            }
        }
        // Pathological case — a thousand collisions on the same base.
        // Fall back to a UUID-derived handle; UUID prefixes have a
        // birthday-bound that's vanishingly unlikely to collide.
        return "u" + userId.toString().substring(0, 8);
    }

    /** Picks the raw seed for a handle, before sanitisation. */
    private String deriveBaseHandle(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (isNonBlank(email)) {
            String local = emailLocalPart(email);
            if (isNonBlank(local)) return local;
        }
        String name = jwt.getClaimAsString("name");
        if (isNonBlank(name)) return name;
        return "";
    }

    /**
     * Sanitises a candidate string to the handle character class:
     * lowercase {@code [a-z0-9-]}. Non-conforming characters become
     * dashes; runs of dashes collapse to one; leading/trailing dashes
     * are stripped. Result is truncated to {@link #MAX_HANDLE_BASE_LENGTH}.
     */
    String sanitizeHandle(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(raw.length());
        boolean lastWasDash = false;
        for (char c : raw.toLowerCase().toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
                lastWasDash = false;
            } else if (!lastWasDash && out.length() > 0) {
                out.append('-');
                lastWasDash = true;
            }
        }
        // Trim trailing dash if any
        while (out.length() > 0 && out.charAt(out.length() - 1) == '-') {
            out.deleteCharAt(out.length() - 1);
        }
        if (out.length() > MAX_HANDLE_BASE_LENGTH) {
            out.setLength(MAX_HANDLE_BASE_LENGTH);
            // After truncation we may again have a trailing dash.
            while (out.length() > 0 && out.charAt(out.length() - 1) == '-') {
                out.deleteCharAt(out.length() - 1);
            }
        }
        return out.toString();
    }

    /**
     * Reads a string from a nested claim like {@code user_metadata.full_name}.
     * Supabase puts a lot of identity claims under {@code user_metadata}.
     */
    @SuppressWarnings("unchecked")
    private String readNestedClaim(Jwt jwt, String parent, String child) {
        Object raw = jwt.getClaim(parent);
        if (raw instanceof Map<?, ?> map) {
            Object value = ((Map<String, Object>) map).get(child);
            if (value instanceof String s) return s;
        }
        return null;
    }

    private static boolean isNonBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
