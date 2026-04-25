package dev.bored.stream.repository;

import dev.bored.stream.entity.PostLike;
import dev.bored.stream.entity.PostLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PostLike} rows.
 *
 * <p>Likes are idempotent by construction — {@code (post_id, user_id)} is
 * the primary key, so a duplicate insert conflicts and a second "like"
 * is a no-op. The service layer will wrap the insert in an
 * {@code ON CONFLICT DO NOTHING} pattern in a later PR; this interface
 * exposes only the read paths needed everywhere (count + membership).</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-19
 */
@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {

    /**
     * Total likes on a given post. Redis counter sits in front in a later PR.
     *
     * @param postId the post id
     * @return like count
     */
    long countByPostId(Long postId);

    /**
     * Whether the given user has liked the given post.
     *
     * @param postId the post id
     * @param userId the user's Supabase UUID
     * @return {@code true} if a like row exists
     */
    boolean existsByPostIdAndUserId(Long postId, UUID userId);
}
