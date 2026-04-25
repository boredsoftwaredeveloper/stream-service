package dev.bored.stream.repository;

import dev.bored.stream.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Post} entities.
 *
 * <p>Only the basic per-id lookup is exposed in this PR. Keyset-paginated
 * feed queries (chronological and heat-ranked) are added in the
 * post-CRUD PR so the query shape can be designed together with its
 * consumers. Live-vs-tombstone filtering also lives in that PR; the
 * lookup here intentionally returns the raw row so callers can decide
 * whether a soft-deleted post is an error (detail view) or a skip
 * (feed list).</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-19
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * Finds a live (non-soft-deleted) post by id.
     *
     * @param postId the post id
     * @return the live post, or empty if missing or tombstoned
     */
    Optional<Post> findByPostIdAndDeletedAtIsNull(Long postId);
}
