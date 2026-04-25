package dev.bored.stream.repository;

import dev.bored.stream.entity.AppUser;
import dev.bored.stream.entity.Post;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice tests for {@link PostRepository}. Exercises JPA round-trip
 * persistence and the soft-delete-aware lookup.
 */
@DataJpaTest
class PostRepositoryTest {

    @Autowired private PostRepository postRepository;
    @Autowired private AppUserRepository appUserRepository;

    private AppUser anAuthor() {
        AppUser u = new AppUser();
        u.setUserId(UUID.randomUUID());
        u.setHandle("author-" + UUID.randomUUID().toString().substring(0, 8));
        u.setDisplayName("Author");
        return appUserRepository.saveAndFlush(u);
    }

    private Post aPost(UUID authorId) {
        Post p = new Post();
        p.setAuthorId(authorId);
        p.setCaption("hello world");
        p.setCodeBody("System.out.println(\"hi\");");
        p.setCodeLanguage("java");
        return p;
    }

    @Test
    void save_persistsPostWithGeneratedId() {
        AppUser author = anAuthor();
        Post saved = postRepository.saveAndFlush(aPost(author.getUserId()));

        assertThat(saved.getPostId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void findByPostIdAndDeletedAtIsNull_returnsLivePost() {
        AppUser author = anAuthor();
        Post saved = postRepository.saveAndFlush(aPost(author.getUserId()));

        Optional<Post> found = postRepository.findByPostIdAndDeletedAtIsNull(saved.getPostId());

        assertThat(found).isPresent();
        assertThat(found.get().getCaption()).isEqualTo("hello world");
    }

    @Test
    void findByPostIdAndDeletedAtIsNull_skipsTombstonedPost() {
        AppUser author = anAuthor();
        Post saved = postRepository.saveAndFlush(aPost(author.getUserId()));
        saved.setDeletedAt(Instant.now());
        postRepository.saveAndFlush(saved);

        Optional<Post> found = postRepository.findByPostIdAndDeletedAtIsNull(saved.getPostId());

        assertThat(found).isEmpty();
    }

    @Test
    void findByPostIdAndDeletedAtIsNull_returnsEmptyForMissingId() {
        Optional<Post> found = postRepository.findByPostIdAndDeletedAtIsNull(999_999L);
        assertThat(found).isEmpty();
    }
}
