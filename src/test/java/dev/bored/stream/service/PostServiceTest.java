package dev.bored.stream.service;

import dev.bored.common.exception.GenericException;
import dev.bored.stream.dto.CreatePostRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}
