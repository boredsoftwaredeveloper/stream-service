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
 * One user's like of one post.
 *
 * <p>The natural composite PK {@code (post_id, user_id)} makes likes
 * idempotent by construction — a second call to "like" this post
 * from the same user conflicts on the primary key and is a no-op.
 * No surrogate id, no need for a separate uniqueness constraint.</p>
 *
 * <p>Redis will hold the fast counter and membership check in a later
 * PR; this table remains the durable source of truth.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-19
 */
@Entity
@Table(name = "post_like")
@IdClass(PostLikeId.class)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PostLike implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
