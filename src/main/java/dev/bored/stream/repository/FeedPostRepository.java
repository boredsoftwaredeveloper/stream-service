package dev.bored.stream.repository;

import dev.bored.stream.entity.FeedPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link FeedPost} entities.
 *
 * @author Bored Software Developer
 * @since 2026-02-15
 */
@Repository
public interface FeedPostRepository extends JpaRepository<FeedPost, Long> {

    /**
     * Retrieves all feed posts ordered by their sort order ascending.
     *
     * @return an ordered list of all {@link FeedPost} records
     */
    List<FeedPost> findAllByOrderBySortOrderAsc();
}
