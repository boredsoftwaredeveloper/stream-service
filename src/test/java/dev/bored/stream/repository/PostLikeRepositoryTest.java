package dev.bored.stream.repository;

import dev.bored.stream.entity.AppUser;
import dev.bored.stream.entity.Post;
import dev.bored.stream.entity.PostLike;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PostLikeRepositoryTest {

    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private AppUserRepository appUserRepository;

    private AppUser user(String handle) {
        AppUser u = new AppUser();
        u.setUserId(UUID.randomUUID());
        u.setHandle(handle);
        u.setDisplayName(handle);
        return appUserRepository.saveAndFlush(u);
    }

    private Post post(UUID authorId) {
        Post p = new Post();
        p.setAuthorId(authorId);
        p.setCaption("c");
        return postRepository.saveAndFlush(p);
    }

    private PostLike like(Long postId, UUID userId) {
        PostLike l = new PostLike();
        l.setPostId(postId);
        l.setUserId(userId);
        return postLikeRepository.saveAndFlush(l);
    }

    @Test
    void save_createsLikeRow() {
        AppUser u = user("liker");
        Post p = post(u.getUserId());

        PostLike saved = like(p.getPostId(), u.getUserId());

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void countByPostId_reflectsLikes() {
        AppUser author = user("author-c");
        Post p = post(author.getUserId());
        like(p.getPostId(), user("a").getUserId());
        like(p.getPostId(), user("b").getUserId());
        like(p.getPostId(), user("c").getUserId());

        assertThat(postLikeRepository.countByPostId(p.getPostId())).isEqualTo(3);
    }

    @Test
    void existsByPostIdAndUserId_reflectsMembership() {
        AppUser u = user("exists");
        AppUser other = user("other");
        Post p = post(u.getUserId());
        like(p.getPostId(), u.getUserId());

        assertThat(postLikeRepository.existsByPostIdAndUserId(p.getPostId(), u.getUserId())).isTrue();
        assertThat(postLikeRepository.existsByPostIdAndUserId(p.getPostId(), other.getUserId())).isFalse();
    }
}
