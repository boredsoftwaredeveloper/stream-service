package dev.bored.stream.repository;

import dev.bored.stream.entity.PostTag;
import dev.bored.stream.entity.PostTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link PostTag} join rows.
 *
 * <p>The Postgres hot path for "posts by tag" lives here as a fallback;
 * the cached path uses a Redis sorted set {@code tag:{slug}} added in a
 * later PR. The DB query stays so that cold tag pages and exotic filters
 * still work without Redis.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-19
 */
@Repository
public interface PostTagRepository extends JpaRepository<PostTag, PostTagId> {

    /**
     * All tag links for a given post.
     *
     * @param postId the post id
     * @return zero or more join rows
     */
    List<PostTag> findByPostId(Long postId);

    /**
     * All post links for a given tag — the fallback inverted index.
     *
     * @param tagId the tag id
     * @return zero or more join rows
     */
    List<PostTag> findByTagId(Long tagId);
}
