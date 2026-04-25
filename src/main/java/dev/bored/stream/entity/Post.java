package dev.bored.stream.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A post in the regret stream.
 *
 * <p>Only the portfolio owner may create posts — that rule is enforced at
 * the service layer by comparing the JWT subject against a configured
 * author UUID, not via a DB constraint, so the rule can be relaxed later
 * without a migration.</p>
 *
 * <p>{@code author_id} is stored as a raw UUID column rather than a
 * {@code @ManyToOne} to {@link AppUser}. We deliberately avoid eager/lazy
 * proxy objects here: feed reads frequently materialise many posts at
 * once and the extra JOIN / proxy machinery adds more cost than it saves
 * at this scale. Join explicitly in queries when author data is needed.</p>
 *
 * <p>Soft-deleted via {@code deleted_at}. The partial index
 * {@code idx_post_created_live} skips tombstones so the feed-list keyset
 * query stays cheap.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-19
 */
@Entity
@Table(name = "post")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Post implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    /** FK to {@link AppUser#getUserId()}. Not a JPA relationship — see class javadoc. */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** The post body. Required, 1-2000 chars (enforced at the DB and DTO validator). */
    @Column(name = "caption", nullable = false, columnDefinition = "TEXT")
    private String caption;

    /** Optional code snippet body. Language-agnostic storage; highlighting is a FE concern. */
    @Column(name = "code_body", columnDefinition = "TEXT")
    private String codeBody;

    /** Optional language hint for syntax highlighting. {@code null} when {@code codeBody} is null. */
    @Column(name = "code_language", length = 40)
    private String codeLanguage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Soft-delete marker. {@code null} means live. */
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
