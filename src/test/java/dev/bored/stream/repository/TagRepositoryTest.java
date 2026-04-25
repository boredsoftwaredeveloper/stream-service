package dev.bored.stream.repository;

import dev.bored.stream.entity.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TagRepositoryTest {

    @Autowired private TagRepository tagRepository;

    private Tag tag(String slug, String display) {
        Tag t = new Tag();
        t.setSlug(slug);
        t.setDisplay(display);
        return t;
    }

    @Test
    void save_persistsTagWithGeneratedId() {
        Tag saved = tagRepository.saveAndFlush(tag("java", "Java"));

        assertThat(saved.getTagId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findBySlug_returnsTagWhenPresent() {
        tagRepository.saveAndFlush(tag("spring", "Spring"));

        Optional<Tag> found = tagRepository.findBySlug("spring");

        assertThat(found).isPresent();
        assertThat(found.get().getDisplay()).isEqualTo("Spring");
    }

    @Test
    void findBySlug_returnsEmptyForMissing() {
        Optional<Tag> found = tagRepository.findBySlug("ghost");
        assertThat(found).isEmpty();
    }
}
