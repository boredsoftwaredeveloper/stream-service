package dev.bored.stream.repository;

import dev.bored.stream.entity.AppUser;
import dev.bored.stream.entity.Post;
import dev.bored.stream.entity.PostTag;
import dev.bored.stream.entity.PostTagId;
import dev.bored.stream.entity.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PostTagRepositoryTest {

    @Autowired private PostTagRepository postTagRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private AppUserRepository appUserRepository;

    private AppUser author() {
        AppUser u = new AppUser();
        u.setUserId(UUID.randomUUID());
        u.setHandle("pt-" + UUID.randomUUID().toString().substring(0, 8));
        u.setDisplayName("Author");
        return appUserRepository.saveAndFlush(u);
    }

    private Post post(UUID authorId) {
        Post p = new Post();
        p.setAuthorId(authorId);
        p.setCaption("c");
        return postRepository.saveAndFlush(p);
    }

    private Tag tag(String slug) {
        Tag t = new Tag();
        t.setSlug(slug);
        t.setDisplay(slug);
        return tagRepository.saveAndFlush(t);
    }

    private PostTag link(Long postId, Long tagId) {
        PostTag pt = new PostTag();
        pt.setPostId(postId);
        pt.setTagId(tagId);
        return postTagRepository.saveAndFlush(pt);
    }

    @Test
    void save_andFindByCompositeKey() {
        Post p = post(author().getUserId());
        Tag t = tag("lookup");

        PostTag saved = link(p.getPostId(), t.getTagId());

        PostTagId id = new PostTagId(saved.getPostId(), saved.getTagId());
        assertThat(postTagRepository.findById(id)).isPresent();
    }

    @Test
    void findByPostId_returnsAllTagsForPost() {
        Post p = post(author().getUserId());
        Tag a = tag("a-slug");
        Tag b = tag("b-slug");
        link(p.getPostId(), a.getTagId());
        link(p.getPostId(), b.getTagId());

        List<PostTag> rows = postTagRepository.findByPostId(p.getPostId());

        assertThat(rows).hasSize(2);
    }

    @Test
    void findByTagId_returnsAllPostsForTag() {
        AppUser u = author();
        Post p1 = post(u.getUserId());
        Post p2 = post(u.getUserId());
        Tag t = tag("shared");
        link(p1.getPostId(), t.getTagId());
        link(p2.getPostId(), t.getTagId());

        List<PostTag> rows = postTagRepository.findByTagId(t.getTagId());

        assertThat(rows).hasSize(2);
    }
}
