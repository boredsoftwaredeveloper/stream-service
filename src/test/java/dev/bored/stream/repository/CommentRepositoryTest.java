package dev.bored.stream.repository;

import dev.bored.stream.entity.AppUser;
import dev.bored.stream.entity.Comment;
import dev.bored.stream.entity.Post;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice tests for {@link CommentRepository}. Covers JPA round-trip,
 * soft-delete-aware lookup, and the self-FK for parent_comment_id.
 */
@DataJpaTest
class CommentRepositoryTest {

    @Autowired private CommentRepository commentRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private AppUserRepository appUserRepository;

    private AppUser aUser(String handle) {
        AppUser u = new AppUser();
        u.setUserId(UUID.randomUUID());
        u.setHandle(handle);
        u.setDisplayName("User " + handle);
        return appUserRepository.saveAndFlush(u);
    }

    private Post aPost(UUID authorId) {
        Post p = new Post();
        p.setAuthorId(authorId);
        p.setCaption("caption");
        return postRepository.saveAndFlush(p);
    }

    private Comment aComment(Long postId, UUID authorId, Long parentCommentId, String body) {
        Comment c = new Comment();
        c.setPostId(postId);
        c.setAuthorId(authorId);
        c.setParentCommentId(parentCommentId);
        c.setBody(body);
        return c;
    }

    @Test
    void save_topLevelAndReply_persistsAndLinks() {
        AppUser author = aUser("auth");
        AppUser replier = aUser("rep");
        Post post = aPost(author.getUserId());

        Comment top = commentRepository.saveAndFlush(
                aComment(post.getPostId(), author.getUserId(), null, "first!"));
        Comment reply = aComment(post.getPostId(), replier.getUserId(), top.getCommentId(), "@auth hi");
        reply.setMentionedUserId(author.getUserId());
        reply = commentRepository.saveAndFlush(reply);

        assertThat(top.getParentCommentId()).isNull();
        assertThat(reply.getParentCommentId()).isEqualTo(top.getCommentId());
        assertThat(reply.getMentionedUserId()).isEqualTo(author.getUserId());
    }

    @Test
    void findByCommentIdAndDeletedAtIsNull_returnsLive() {
        AppUser author = aUser("live-auth");
        Post post = aPost(author.getUserId());
        Comment c = commentRepository.saveAndFlush(
                aComment(post.getPostId(), author.getUserId(), null, "live"));

        Optional<Comment> found = commentRepository.findByCommentIdAndDeletedAtIsNull(c.getCommentId());

        assertThat(found).isPresent();
        assertThat(found.get().getBody()).isEqualTo("live");
    }

    @Test
    void findByCommentIdAndDeletedAtIsNull_skipsTombstoned() {
        AppUser author = aUser("tomb-auth");
        Post post = aPost(author.getUserId());
        Comment c = commentRepository.saveAndFlush(
                aComment(post.getPostId(), author.getUserId(), null, "dying"));
        c.setDeletedAt(Instant.now());
        commentRepository.saveAndFlush(c);

        Optional<Comment> found = commentRepository.findByCommentIdAndDeletedAtIsNull(c.getCommentId());

        assertThat(found).isEmpty();
    }
}
