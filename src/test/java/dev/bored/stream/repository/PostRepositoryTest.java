package dev.bored.stream.repository;

import dev.bored.stream.entity.AppUser;
import dev.bored.stream.entity.Post;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
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
    @Autowired private EntityManager entityManager;

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

    // ── Keyset pagination ──────────────────────────────────────────────

    /**
     * Builds 5 posts with ascending timestamps, returns them in the
     * order created (oldest first). The keyset queries return DESC, so
     * subsequent assertions reverse this order.
     */
    private List<Post> seedFivePosts() {
        AppUser author = anAuthor();
        return List.of(
                savePostAt(author.getUserId(), Instant.parse("2026-04-25T10:00:00Z")),
                savePostAt(author.getUserId(), Instant.parse("2026-04-25T10:01:00Z")),
                savePostAt(author.getUserId(), Instant.parse("2026-04-25T10:02:00Z")),
                savePostAt(author.getUserId(), Instant.parse("2026-04-25T10:03:00Z")),
                savePostAt(author.getUserId(), Instant.parse("2026-04-25T10:04:00Z"))
        );
    }

    /**
     * Inserts a post then forces its {@code created_at} to the given
     * value via a native UPDATE.
     *
     * <p>The entity declares {@code @Column(updatable = false)} on
     * {@code created_at} — Hibernate respects that, so a normal
     * {@code save()} won't overwrite the {@code @CreationTimestamp}
     * value. For tests we bypass JPA with a native query, which is the
     * standard "I really mean it" escape hatch.</p>
     */
    private Post savePostAt(UUID authorId, Instant createdAt) {
        Post p = new Post();
        p.setAuthorId(authorId);
        p.setCaption("post-at-" + createdAt);
        Post saved = postRepository.saveAndFlush(p);

        entityManager.createNativeQuery("UPDATE post SET created_at = ?1 WHERE post_id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, saved.getPostId())
                .executeUpdate();
        entityManager.clear();   // drop the now-stale entity from the persistence context

        return postRepository.findById(saved.getPostId()).orElseThrow();
    }

    @Test
    void findChronoFirstPage_returnsNewestFirst() {
        seedFivePosts();

        List<Post> page = postRepository.findChronoFirstPage(PageRequest.of(0, 3));

        assertThat(page).hasSize(3);
        // Newest first: 10:04, 10:03, 10:02
        assertThat(page.get(0).getCaption()).isEqualTo("post-at-2026-04-25T10:04:00Z");
        assertThat(page.get(1).getCaption()).isEqualTo("post-at-2026-04-25T10:03:00Z");
        assertThat(page.get(2).getCaption()).isEqualTo("post-at-2026-04-25T10:02:00Z");
    }

    @Test
    void findChronoFirstPage_skipsTombstonedRows() {
        AppUser author = anAuthor();
        Post live = savePostAt(author.getUserId(), Instant.parse("2026-04-25T10:00:00Z"));
        Post dead = savePostAt(author.getUserId(), Instant.parse("2026-04-25T10:01:00Z"));
        dead.setDeletedAt(Instant.now());
        postRepository.saveAndFlush(dead);

        List<Post> page = postRepository.findChronoFirstPage(PageRequest.of(0, 10));

        assertThat(page).extracting(Post::getPostId)
                .containsExactly(live.getPostId());
    }

    @Test
    void findChronoPageAfter_resumesStrictlyBelowCursor() {
        List<Post> seeded = seedFivePosts();
        // Pretend we just returned the newest 2 (indices 4, 3) and the
        // cursor is the boundary at index 3 (10:03).
        Post boundary = seeded.get(3);

        List<Post> nextPage = postRepository.findChronoPageAfter(
                boundary.getCreatedAt(), boundary.getPostId(),
                PageRequest.of(0, 10));

        // We expect 10:02, 10:01, 10:00 — the three rows strictly below
        // the boundary, in DESC order. The boundary row itself must NOT
        // appear (that's what makes it "keyset" — strictly less, no
        // duplicates, no skips).
        assertThat(nextPage).hasSize(3);
        assertThat(nextPage.get(0).getCaption()).isEqualTo("post-at-2026-04-25T10:02:00Z");
        assertThat(nextPage.get(2).getCaption()).isEqualTo("post-at-2026-04-25T10:00:00Z");
    }

    @Test
    void findChronoPageAfter_handlesTimestampTie_viaPostIdTiebreaker() {
        AppUser author = anAuthor();
        Instant sharedTs = Instant.parse("2026-04-25T10:00:00Z");
        Post a = savePostAt(author.getUserId(), sharedTs);
        Post b = savePostAt(author.getUserId(), sharedTs);
        Post c = savePostAt(author.getUserId(), sharedTs);

        // a, b, c all share the same created_at. ORDER BY DESC gives us
        // c, b, a (largest post_id first). Cursor at 'b' should return
        // only 'a' — same timestamp, smaller id.
        List<Post> afterB = postRepository.findChronoPageAfter(
                b.getCreatedAt(), b.getPostId(), PageRequest.of(0, 10));

        assertThat(afterB).extracting(Post::getPostId)
                .containsExactly(a.getPostId());
    }
}
