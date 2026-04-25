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
 * A flat comment on a {@link Post}, optionally a reply to another comment.
 *
 * <p>Instagram-style: replies are exactly one level deep.
 * {@code parent_comment_id} only ever points at a top-level comment
 * (one whose {@code parent_comment_id} is {@code null}). When a user
 * taps "reply" on a reply, the service layer re-parents the new comment
 * to the original top-level comment and records the @mention in
 * {@code mentioned_user_id}. No recursive traversal ever needs to
 * happen for display.</p>
 *
 * <p>{@code post_id}, {@code author_id}, {@code parent_comment_id} and
 * {@code mentioned_user_id} are raw FK columns, not {@code @ManyToOne}
 * relationships — same reasoning as {@link Post}.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-19
 */
@Entity
@Table(name = "comment")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Comment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;

    /** FK to {@link Post#getPostId()}. */
    @Column(name = "post_id", nullable = false)
    private Long postId;

    /** FK to {@link AppUser#getUserId()}. */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /**
     * Null for top-level comments; otherwise the id of the top-level
     * comment this reply hangs off. Never points at another reply.
     */
    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    /**
     * When this is a reply and the user @mentioned someone, their user_id
     * is recorded here — usually the author of the reply they tapped.
     * {@code null} for top-level comments and unprefixed replies.
     */
    @Column(name = "mentioned_user_id")
    private UUID mentionedUserId;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Soft-delete marker. {@code null} means live. */
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
