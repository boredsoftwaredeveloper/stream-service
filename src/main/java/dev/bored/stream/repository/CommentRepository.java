package dev.bored.stream.repository;

import dev.bored.stream.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Comment} entities.
 *
 * <p>Listing queries (top-level comments on a post, replies under a
 * top-level comment) are added in the comment-CRUD PR. This PR exposes
 * only the lookups needed by the authorization checks and the
 * "comment exists + not deleted" guard.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-19
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Finds a live (non-soft-deleted) comment by id.
     *
     * @param commentId the comment id
     * @return the live comment, or empty if missing or tombstoned
     */
    Optional<Comment> findByCommentIdAndDeletedAtIsNull(Long commentId);
}
