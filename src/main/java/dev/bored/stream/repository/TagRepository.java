package dev.bored.stream.repository;

import dev.bored.stream.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Tag} entities.
 *
 * <p>Tags are interned: the service layer normalises the display form
 * into a slug and upserts via {@link #findBySlug(String)} + save.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-19
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * Looks up a tag by its canonical lowercase slug.
     *
     * @param slug the lowercased slug
     * @return the tag, or empty if not yet interned
     */
    Optional<Tag> findBySlug(String slug);
}
