package dev.bored.stream.service;

import dev.bored.common.exception.GenericException;
import dev.bored.stream.dto.CreatePostRequest;
import dev.bored.stream.dto.FeedPageDTO;
import dev.bored.stream.dto.PostAuthorDTO;
import dev.bored.stream.dto.PostDTO;
import dev.bored.stream.entity.AppUser;
import dev.bored.stream.entity.Post;
import dev.bored.stream.mapper.PostMapper;
import dev.bored.stream.repository.AppUserRepository;
import dev.bored.stream.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Mock private PostRepository postRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private PostMapper postMapper;

    @InjectMocks private PostService postService;

    private Jwt authorJwt;
    private AppUser authorUser;
    private Post entity;
    private PostDTO dto;

    @BeforeEach
    void setUp() {
        authorJwt = Jwt.withTokenValue("test")
                .header("alg", "ES256")
                .subject(AUTHOR_ID.toString())
                .build();

        authorUser = new AppUser();
        authorUser.setUserId(AUTHOR_ID);
        authorUser.setHandle("seth");
        authorUser.setDisplayName("Seth");

        entity = new Post();
        entity.setPostId(42L);
        entity.setAuthorId(AUTHOR_ID);
        entity.setCaption("hello");
        entity.setCreatedAt(Instant.parse("2026-04-25T10:00:00Z"));

        dto = PostDTO.builder()
                .postId(42L)
                .author(PostAuthorDTO.builder()
                        .userId(AUTHOR_ID)
                        .handle("seth")
                        .displayName("Seth")
                        .build())
                .caption("hello")
                .createdAt(entity.getCreatedAt())
                .build();
    }

    // ── createPost ──────────────────────────────────────────────────────

    @Test
    void createPost_savesEntity_withAuthorIdFromJwt() {
        CreatePostRequest req = CreatePostRequest.builder()
                .caption("hello")
                .codeBody("System.out.println(\"hi\");")
                .codeLanguage("java")
                .build();

        Post mappedEntity = new Post();
        mappedEntity.setCaption("hello");
        mappedEntity.setCodeBody("System.out.println(\"hi\");");
        mappedEntity.setCodeLanguage("java");

        when(postMapper.toEntity(req)).thenReturn(mappedEntity);
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setPostId(42L);
            return p;
        });
        when(appUserRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(authorUser));
        when(postMapper.toDTO(any(Post.class), any(AppUser.class))).thenReturn(dto);

        PostDTO result = postService.createPost(authorJwt, req);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthorId()).isEqualTo(AUTHOR_ID);
        assertThat(result).isEqualTo(dto);
    }

    // ── getPostById ─────────────────────────────────────────────────────

    @Test
    void getPostById_returnsHydratedDTO_whenLive() {
        when(postRepository.findByPostIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(entity));
        when(appUserRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(authorUser));
        when(postMapper.toDTO(entity, authorUser)).thenReturn(dto);

        PostDTO result = postService.getPostById(42L);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    void getPostById_throws404_whenMissingOrTombstoned() {
        when(postRepository.findByPostIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostById(99L))
                .isInstanceOfSatisfying(GenericException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPostById_throws500_whenAuthorRowMissing() {
        when(postRepository.findByPostIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(entity));
        when(appUserRepository.findById(AUTHOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostById(42L))
                .isInstanceOfSatisfying(GenericException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    // ── deletePost ──────────────────────────────────────────────────────

    @Test
    void deletePost_setsDeletedAt_andSaves() {
        when(postRepository.findByPostIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(entity));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        postService.deletePost(authorJwt, 42L);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void deletePost_throws404_whenMissingOrTombstoned() {
        when(postRepository.findByPostIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.deletePost(authorJwt, 99L))
                .isInstanceOfSatisfying(GenericException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(postRepository, never()).save(any());
    }

    // ── listChronological ──────────────────────────────────────────────

    /**
     * Helper: builds {@code n} mock {@link Post} entities with descending
     * timestamps starting at the given epoch. Mirrors what the keyset
     * query would actually return (newest first).
     */
    private List<Post> mockPostsDesc(int n, Instant newest) {
        Post[] posts = new Post[n];
        for (int i = 0; i < n; i++) {
            Post p = new Post();
            p.setPostId((long) (1000 - i));
            p.setAuthorId(AUTHOR_ID);
            p.setCaption("post-" + i);
            p.setCreatedAt(newest.minusSeconds(i));
            posts[i] = p;
        }
        return List.of(posts);
    }

    @Test
    void listChronological_noCursor_callsFirstPageQuery() {
        List<Post> rows = mockPostsDesc(3, Instant.parse("2026-04-25T10:00:00Z"));
        when(postRepository.findChronoFirstPage(any(Pageable.class))).thenReturn(rows);
        when(appUserRepository.findAllById(any())).thenReturn(List.of(authorUser));
        when(postMapper.toDTO(any(Post.class), any(AppUser.class))).thenReturn(dto);

        FeedPageDTO page = postService.listChronological(null, 20);

        assertThat(page.getItems()).hasSize(3);
        assertThat(page.getNextCursor()).isNull();   // hasMore=false (3 < 21 fetch limit)
        verify(postRepository).findChronoFirstPage(PageRequest.of(0, 21));
    }

    @Test
    void listChronological_withCursor_callsKeysetAfterQuery() {
        Instant ts = Instant.parse("2026-04-25T10:00:00Z");
        Long id = 42L;
        String cursor = FeedCursor.encode(ts, id);

        List<Post> rows = mockPostsDesc(2, ts.minusSeconds(60));
        when(postRepository.findChronoPageAfter(eq(ts), eq(id), any(Pageable.class)))
                .thenReturn(rows);
        when(appUserRepository.findAllById(any())).thenReturn(List.of(authorUser));
        when(postMapper.toDTO(any(Post.class), any(AppUser.class))).thenReturn(dto);

        FeedPageDTO page = postService.listChronological(cursor, 5);

        assertThat(page.getItems()).hasSize(2);
        verify(postRepository).findChronoPageAfter(ts, id, PageRequest.of(0, 6));
    }

    @Test
    void listChronological_emitsNextCursor_whenHasMore() {
        // Ask for size 2; service requests 3 (size + 1). DB returns 3 →
        // service trims to 2 and emits the boundary as nextCursor.
        Instant newest = Instant.parse("2026-04-25T10:00:00Z");
        List<Post> rows = mockPostsDesc(3, newest);   // post 1000, 999, 998
        when(postRepository.findChronoFirstPage(any(Pageable.class))).thenReturn(rows);
        when(appUserRepository.findAllById(any())).thenReturn(List.of(authorUser));
        when(postMapper.toDTO(any(Post.class), any(AppUser.class))).thenReturn(dto);

        FeedPageDTO page = postService.listChronological(null, 2);

        assertThat(page.getItems()).hasSize(2);
        assertThat(page.getNextCursor()).isNotNull();
        FeedCursor decoded = FeedCursor.decode(page.getNextCursor());
        // Boundary should be the LAST returned row (post 999), not the
        // sentinel +1 row (post 998).
        assertThat(decoded.postId()).isEqualTo(999L);
        assertThat(decoded.ts()).isEqualTo(newest.minusSeconds(1));
    }

    @Test
    void listChronological_emptyResult_returnsEmptyAndNullCursor() {
        when(postRepository.findChronoFirstPage(any(Pageable.class))).thenReturn(List.of());

        FeedPageDTO page = postService.listChronological(null, 20);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void listChronological_clampsHugeSize_to100() {
        when(postRepository.findChronoFirstPage(any(Pageable.class))).thenReturn(List.of());

        postService.listChronological(null, 1_000_000);

        // 100 (cap) + 1 (sentinel) = 101 fetch size
        verify(postRepository).findChronoFirstPage(PageRequest.of(0, 101));
    }

    @Test
    void listChronological_clampsZeroOrNegative_toDefault() {
        when(postRepository.findChronoFirstPage(any(Pageable.class))).thenReturn(List.of());

        postService.listChronological(null, 0);
        postService.listChronological(null, -5);
        postService.listChronological(null, null);

        // Default 20 + 1 sentinel = 21 fetch size, three calls.
        verify(postRepository, org.mockito.Mockito.times(3))
                .findChronoFirstPage(PageRequest.of(0, 21));
    }

    @Test
    void listChronological_propagatesMalformedCursorAs400() {
        assertThatThrownBy(() -> postService.listChronological("garbage~~~not~base64", 20))
                .isInstanceOfSatisfying(GenericException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
