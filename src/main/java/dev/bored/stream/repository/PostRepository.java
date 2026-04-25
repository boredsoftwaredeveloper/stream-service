package dev.bored.stream.repository;

import dev.bored.stream.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
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

    /**
     * First page of the chronological feed — newest first, live only.
     *
     * <p>Walks the partial index {@code idx_post_created_live} backwards
     * (DESC). The {@link Pageable} parameter is used purely for its
     * limit; offset must be zero (keyset pagination doesn't skip rows,
     * it filters them).</p>
     *
     * @param pageable a {@code PageRequest.of(0, size + 1)} — the +1 is
     *                 the "is there more?" sentinel row
     * @return up to {@code pageable.pageSize} posts, newest first
     */
    @Query("SELECT p FROM Post p " +
           "WHERE p.deletedAt IS NULL " +
           "ORDER BY p.createdAt DESC, p.postId DESC")
    List<Post> findChronoFirstPage(Pageable pageable);

    /**
     * Subsequent page of the chronological feed — newest first, live only,
     * starting strictly below the cursor boundary.
     *
     * <p>The WHERE clause is the textbook keyset filter: {@code created_at}
     * strictly less than the cursor's timestamp, OR equal-and-{@code post_id}
     * strictly less. That's the row-comparison "less-than" expanded into
     * portable JPQL — Postgres also supports the row-constructor form
     * {@code (created_at, post_id) < (:ts, :id)} but H2 doesn't, so we
     * spell it out for cross-DB test compatibility.</p>
     *
     * <p>Postgres uses the partial index {@code idx_post_created_live}
     * to seek directly to the cursor and stream results in index order
     * — no sort, no scan past tombstones. This is the whole point of
     * keyset pagination: no matter how deep the user scrolls, page
     * cost stays constant.</p>
     *
     * @param cursorTs the {@code created_at} of the last row on the previous page
     * @param cursorId the {@code post_id} of the last row on the previous page
     * @param pageable {@code PageRequest.of(0, size + 1)} — same {@code +1} trick
     * @return up to {@code pageable.pageSize} posts strictly below the cursor
     */
    @Query("SELECT p FROM Post p " +
           "WHERE p.deletedAt IS NULL " +
           "  AND (p.createdAt < :cursorTs " +
           "       OR (p.createdAt = :cursorTs AND p.postId < :cursorId)) " +
           "ORDER BY p.createdAt DESC, p.postId DESC")
    List<Post> findChronoPageAfter(@Param("cursorTs") Instant cursorTs,
                                   @Param("cursorId") Long cursorId,
                                   Pageable pageable);
}
