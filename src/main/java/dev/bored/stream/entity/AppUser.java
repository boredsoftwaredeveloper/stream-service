package dev.bored.stream.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Mirror of a Supabase auth user, keyed by the JWT {@code sub} claim.
 *
 * <p>The {@code app_user} table is populated lazily — the first time an
 * authenticated user performs any action, we upsert their identity from
 * the JWT claims. We don't pre-seed from Supabase. This lets the portfolio
 * service join feed content against a local user row without dragging in
 * a second round-trip to Supabase for every read.</p>
 *
 * <p>Display name and avatar can drift from the Supabase source of truth
 * between visits; that's acceptable — we refresh them on every
 * authenticated write.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-19
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AppUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Supabase JWT {@code sub} claim — the user's durable identity. */
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** URL-safe display handle, unique across all users. */
    @Column(name = "handle", nullable = false, length = 40, unique = true)
    private String handle;

    /** Human-readable display name shown next to posts and comments. */
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /** Optional avatar URL copied from Supabase user metadata. */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** First time we observed this user. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Last time we refreshed handle / display name / avatar from a JWT. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
