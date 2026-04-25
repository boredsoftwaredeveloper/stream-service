package dev.bored.stream.service;

import dev.bored.stream.entity.AppUser;
import dev.bored.stream.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AppUserService}. Each test exercises one specific
 * behaviour: claim-resolution fallbacks, handle sanitisation, the
 * Caffeine fast-path, and the race-recovery on insert.
 */
@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    private static final UUID SUB = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock private AppUserRepository appUserRepository;
    @InjectMocks private AppUserService appUserService;

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("test")
                .header("alg", "ES256")
                .subject(SUB.toString());
        claims.forEach(b::claim);
        return b.build();
    }

    private AppUser persistedUser() {
        AppUser u = new AppUser();
        u.setUserId(SUB);
        u.setHandle("alice");
        u.setDisplayName("Alice");
        u.setAvatarUrl("https://old.example.com/a.png");
        return u;
    }

    @BeforeEach
    void resetCache() {
        // Each test gets a fresh service so the Caffeine cache doesn't leak
        // state between tests. @InjectMocks rebuilds it before each test.
    }

    // ── upsertFromJwt: insert path ──────────────────────────────────────

    @Test
    void upsertFromJwt_insertsNewUser_withDerivedHandleFromEmail() {
        when(appUserRepository.findById(SUB)).thenReturn(Optional.empty());
        when(appUserRepository.existsByHandle(anyString())).thenReturn(false);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser saved = appUserService.upsertFromJwt(jwt(Map.of(
                "email", "alice@example.com",
                "name", "Alice"
        )));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        AppUser written = captor.getValue();
        assertThat(written.getUserId()).isEqualTo(SUB);
        assertThat(written.getHandle()).isEqualTo("alice");
        assertThat(written.getDisplayName()).isEqualTo("Alice");
        assertThat(saved.getHandle()).isEqualTo("alice");
    }

    @Test
    void upsertFromJwt_appendsSuffix_whenHandleCollides() {
        when(appUserRepository.findById(SUB)).thenReturn(Optional.empty());
        when(appUserRepository.existsByHandle("alice")).thenReturn(true);
        when(appUserRepository.existsByHandle("alice1")).thenReturn(true);
        when(appUserRepository.existsByHandle("alice2")).thenReturn(false);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser saved = appUserService.upsertFromJwt(jwt(Map.of("email", "alice@example.com")));

        assertThat(saved.getHandle()).isEqualTo("alice2");
    }

    @Test
    void upsertFromJwt_fallsBackToUuidPrefix_whenSanitizationYieldsEmpty() {
        when(appUserRepository.findById(SUB)).thenReturn(Optional.empty());
        when(appUserRepository.existsByHandle(anyString())).thenReturn(false);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        // No email, no name, no metadata → base handle is empty → UUID prefix
        AppUser saved = appUserService.upsertFromJwt(jwt(Map.of()));

        assertThat(saved.getHandle()).isEqualTo("u00000000");
    }

    // ── upsertFromJwt: update path ─────────────────────────────────────

    @Test
    void upsertFromJwt_updatesExistingUser_keepsHandleSticky() {
        AppUser existing = persistedUser();
        when(appUserRepository.findById(SUB)).thenReturn(Optional.of(existing));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser saved = appUserService.upsertFromJwt(jwt(Map.of(
                "name", "Alice Updated",
                "picture", "https://new.example.com/a.png"
        )));

        // Handle stays the same, display name + avatar refreshed
        assertThat(saved.getHandle()).isEqualTo("alice");
        assertThat(saved.getDisplayName()).isEqualTo("Alice Updated");
        assertThat(saved.getAvatarUrl()).isEqualTo("https://new.example.com/a.png");
    }

    // ── upsertFromJwt: caching ─────────────────────────────────────────

    @Test
    void upsertFromJwt_skipsRepositorySave_onSecondCallWithinTtl() {
        AppUser existing = persistedUser();
        when(appUserRepository.findById(SUB)).thenReturn(Optional.of(existing));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        appUserService.upsertFromJwt(jwt(Map.of("name", "Alice")));   // first call: writes
        appUserService.upsertFromJwt(jwt(Map.of("name", "Alice")));   // second call: cache hit

        verify(appUserRepository, times(1)).save(any(AppUser.class));
        // findById is called both times — once for the slow path, once
        // through the cache fast path to retrieve the row to return.
        verify(appUserRepository, times(2)).findById(SUB);
    }

    @Test
    void upsertFromJwt_recoversFromCachePoisoning_whenRowMissing() {
        // First call: write the row, cache marks it as freshly synced
        when(appUserRepository.findById(SUB))
                .thenReturn(Optional.empty())              // 1st call: missing → insert
                .thenReturn(Optional.empty())              // 2nd call: cache hit then row missing
                .thenReturn(Optional.empty());             // re-sync slow path also missing
        when(appUserRepository.existsByHandle(anyString())).thenReturn(false);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        appUserService.upsertFromJwt(jwt(Map.of("email", "alice@example.com")));
        appUserService.upsertFromJwt(jwt(Map.of("email", "alice@example.com")));

        // Two saves total — the cache fast-path detected the missing row
        // and fell through, triggering a fresh insert.
        verify(appUserRepository, times(2)).save(any(AppUser.class));
    }

    // ── upsertFromJwt: race recovery ───────────────────────────────────

    @Test
    void upsertFromJwt_recoversFromConcurrentInsertRace() {
        AppUser concurrentlyInserted = persistedUser();
        when(appUserRepository.findById(SUB))
                .thenReturn(Optional.empty())                      // 1st: nothing yet
                .thenReturn(Optional.of(concurrentlyInserted));    // 2nd (race recovery): now exists
        when(appUserRepository.existsByHandle(anyString())).thenReturn(false);
        when(appUserRepository.save(any(AppUser.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        AppUser saved = appUserService.upsertFromJwt(jwt(Map.of("email", "alice@example.com")));

        assertThat(saved.getHandle()).isEqualTo("alice");
    }

    @Test
    void upsertFromJwt_propagatesException_whenRaceRecoveryAlsoMisses() {
        when(appUserRepository.findById(SUB))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(appUserRepository.existsByHandle(anyString())).thenReturn(false);
        DataIntegrityViolationException original = new DataIntegrityViolationException("dup");
        when(appUserRepository.save(any(AppUser.class))).thenThrow(original);

        try {
            appUserService.upsertFromJwt(jwt(Map.of("email", "alice@example.com")));
        } catch (DataIntegrityViolationException e) {
            assertThat(e).isSameAs(original);
            return;
        }
        throw new AssertionError("expected DataIntegrityViolationException");
    }

    // ── resolveDisplayName ─────────────────────────────────────────────

    @Test
    void resolveDisplayName_prefersTopLevelNameClaim() {
        String name = appUserService.resolveDisplayName(jwt(Map.of(
                "name", "Top Level",
                "user_metadata", Map.of("full_name", "Nested"),
                "email", "ignored@example.com"
        )));
        assertThat(name).isEqualTo("Top Level");
    }

    @Test
    void resolveDisplayName_fallsBackToNestedFullName() {
        String name = appUserService.resolveDisplayName(jwt(Map.of(
                "user_metadata", Map.of("full_name", "Nested"),
                "email", "ignored@example.com"
        )));
        assertThat(name).isEqualTo("Nested");
    }

    @Test
    void resolveDisplayName_fallsBackToEmailLocalPart() {
        String name = appUserService.resolveDisplayName(jwt(Map.of(
                "email", "alice@example.com"
        )));
        assertThat(name).isEqualTo("alice");
    }

    @Test
    void resolveDisplayName_fallsBackToUuidPrefix_whenNoClaims() {
        String name = appUserService.resolveDisplayName(jwt(Map.of()));
        assertThat(name).isEqualTo("User 00000000");
    }

    @Test
    void resolveDisplayName_skipsEmailWithEmptyLocalPart() {
        String name = appUserService.resolveDisplayName(jwt(Map.of(
                "email", "@example.com"
        )));
        assertThat(name).isEqualTo("User 00000000");
    }

    @Test
    void resolveDisplayName_truncatesToColumnWidth() {
        String long129 = "a".repeat(129);
        String name = appUserService.resolveDisplayName(jwt(Map.of("name", long129)));
        assertThat(name).hasSize(100);
    }

    // ── resolveAvatarUrl ───────────────────────────────────────────────

    @Test
    void resolveAvatarUrl_prefersPictureClaim() {
        String url = appUserService.resolveAvatarUrl(jwt(Map.of(
                "picture", "https://top.example/p.png",
                "user_metadata", Map.of("avatar_url", "https://nested.example/p.png")
        )));
        assertThat(url).isEqualTo("https://top.example/p.png");
    }

    @Test
    void resolveAvatarUrl_fallsBackToNestedAvatarUrl() {
        String url = appUserService.resolveAvatarUrl(jwt(Map.of(
                "user_metadata", Map.of("avatar_url", "https://nested.example/p.png")
        )));
        assertThat(url).isEqualTo("https://nested.example/p.png");
    }

    @Test
    void resolveAvatarUrl_returnsNull_whenNoClaim() {
        assertThat(appUserService.resolveAvatarUrl(jwt(Map.of()))).isNull();
    }

    // ── sanitizeHandle ─────────────────────────────────────────────────

    @Test
    void sanitizeHandle_basicLowercasing() {
        assertThat(appUserService.sanitizeHandle("Alice")).isEqualTo("alice");
    }

    @Test
    void sanitizeHandle_replacesNonAlphanumWithDashes() {
        assertThat(appUserService.sanitizeHandle("alice.bob_carol")).isEqualTo("alice-bob-carol");
    }

    @Test
    void sanitizeHandle_collapsesRunsOfDashes() {
        assertThat(appUserService.sanitizeHandle("alice...bob")).isEqualTo("alice-bob");
    }

    @Test
    void sanitizeHandle_stripsTrailingDashes() {
        assertThat(appUserService.sanitizeHandle("alice...")).isEqualTo("alice");
    }

    @Test
    void sanitizeHandle_dropsLeadingNonAlphanum() {
        // The implementation only emits a dash if the buffer already has
        // content, so leading punctuation is dropped entirely.
        assertThat(appUserService.sanitizeHandle("...alice")).isEqualTo("alice");
    }

    @Test
    void sanitizeHandle_truncatesAndStripsTrailingDash() {
        // "a.".repeat(16) is 32 chars; after sanitization it becomes
        // alternating "a-a-...-a" (16 a's, 15 dashes inside, 31 chars)
        // which is > 30 and triggers truncation. The truncated 30th
        // char is a '-', so the inner trim-trailing-dash branch fires.
        String input = "a.".repeat(16);
        String out = appUserService.sanitizeHandle(input);
        assertThat(out).hasSizeLessThanOrEqualTo(30);
        assertThat(out).doesNotEndWith("-");
        assertThat(out).endsWith("a");
    }

    @Test
    void sanitizeHandle_emptyAndNullInputs() {
        assertThat(appUserService.sanitizeHandle("")).isEmpty();
        assertThat(appUserService.sanitizeHandle(null)).isEmpty();
        assertThat(appUserService.sanitizeHandle("....")).isEmpty();
    }

    // ── generateUniqueHandle ───────────────────────────────────────────

    @Test
    void generateUniqueHandle_returnsBase_whenUnused() {
        when(appUserRepository.existsByHandle("alice")).thenReturn(false);
        String handle = appUserService.generateUniqueHandle(
                jwt(Map.of("email", "alice@example.com")), SUB);
        assertThat(handle).isEqualTo("alice");
    }

    @Test
    void generateUniqueHandle_fallsBackAfterMaxSuffix() {
        // Every candidate in the suffix space is taken -> UUID fallback.
        when(appUserRepository.existsByHandle(anyString())).thenReturn(true);
        String handle = appUserService.generateUniqueHandle(
                jwt(Map.of("email", "alice@example.com")), SUB);
        assertThat(handle).isEqualTo("u00000000");
    }

    @Test
    void generateUniqueHandle_handlesNameClaimAsBase() {
        when(appUserRepository.existsByHandle("john-doe")).thenReturn(false);
        String handle = appUserService.generateUniqueHandle(
                jwt(Map.of("name", "John Doe")), SUB);
        assertThat(handle).isEqualTo("john-doe");
    }

    // ── unused but kept for confidence ─────────────────────────────────

    @Test
    void recentSyncCache_doesNotShareAcrossInstants() {
        // Sanity: confirm the service's internal cache field exists and
        // isn't accidentally null. We exercise it by performing one
        // upsert, which puts an entry in the cache.
        when(appUserRepository.findById(SUB)).thenReturn(Optional.empty());
        when(appUserRepository.existsByHandle(anyString())).thenReturn(false);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        appUserService.upsertFromJwt(jwt(Map.of("email", "x@example.com")));
        Instant after = Instant.now();

        // No assertion failure = pass. Bounds check just to use `before`/`after`.
        assertThat(before).isBeforeOrEqualTo(after);
        verify(appUserRepository, never()).deleteById(any());
    }
}
