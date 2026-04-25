package dev.bored.stream.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bored.common.exception.CommonExceptionHandler;
import dev.bored.common.exception.GenericException;
import dev.bored.stream.config.SecurityConfig;
import dev.bored.stream.config.StreamProperties;
import dev.bored.stream.dto.CreatePostRequest;
import dev.bored.stream.dto.FeedPageDTO;
import dev.bored.stream.dto.PostAuthorDTO;
import dev.bored.stream.dto.PostDTO;
import dev.bored.stream.exception.AuthorizationExceptionHandler;
import dev.bored.stream.interceptor.JwtUserSyncInterceptor;
import dev.bored.stream.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link PostController}. Verifies routing, validation,
 * the read/write split, and the {@code @PreAuthorize} author gate.
 *
 * <p>Authenticated requests use the {@code jwt()} request post-processor
 * to install a real {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken}
 * — which is what {@code @AuthenticationPrincipal Jwt} expects. The
 * older {@code @WithMockUser} sets up a generic UserDetails principal
 * that can't be cast to {@code Jwt}.</p>
 */
@WebMvcTest(PostController.class)
@Import({SecurityConfig.class,
         CommonExceptionHandler.class,
         AuthorizationExceptionHandler.class,
         StreamProperties.class})
class PostControllerTest {

    private static final String AUTHOR_UUID = "00000000-0000-0000-0000-000000000099";
    private static final String OTHER_UUID  = "00000000-0000-0000-0000-000000000001";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private PostService postService;
    @MockitoBean private JwtUserSyncInterceptor jwtUserSyncInterceptor;

    @BeforeEach
    void allowInterceptor() throws Exception {
        // Mocked HandlerInterceptor.preHandle defaults to false → blocks
        // every request unless we explicitly let it through. The real
        // StreamProperties (imported above) loads its author-user-id from
        // the test application.yml — see resources/application.yml.
        when(jwtUserSyncInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    private CreatePostRequest validRequest() {
        return CreatePostRequest.builder()
                .caption("hello world")
                .codeBody("System.out.println(\"hi\");")
                .codeLanguage("java")
                .build();
    }

    private PostDTO sampleDTO() {
        return PostDTO.builder()
                .postId(42L)
                .author(PostAuthorDTO.builder()
                        .userId(UUID.fromString(AUTHOR_UUID))
                        .handle("seth")
                        .displayName("Seth")
                        .build())
                .caption("hello world")
                .codeBody("System.out.println(\"hi\");")
                .codeLanguage("java")
                .createdAt(Instant.parse("2026-04-25T10:00:00Z"))
                .build();
    }

    // ── createPost ──────────────────────────────────────────────────────

    @Test
    void createPost_asAuthor_returns201() throws Exception {
        when(postService.createPost(any(), any())).thenReturn(sampleDTO());

        mockMvc.perform(post("/api/v1/feed/v2")
                        .with(jwt().jwt(j -> j.subject(AUTHOR_UUID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(42))
                .andExpect(jsonPath("$.author.handle").value("seth"))
                .andExpect(jsonPath("$.caption").value("hello world"));
    }

    @Test
    void createPost_asNonAuthor_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/feed/v2")
                        .with(jwt().jwt(j -> j.subject(OTHER_UUID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());

        verify(postService, never()).createPost(any(), any());
    }

    @Test
    void createPost_asAnonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/feed/v2")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());

        verify(postService, never()).createPost(any(), any());
    }

    @Test
    void createPost_withBlankCaption_returns400() throws Exception {
        CreatePostRequest req = CreatePostRequest.builder().caption("   ").build();

        mockMvc.perform(post("/api/v1/feed/v2")
                        .with(jwt().jwt(j -> j.subject(AUTHOR_UUID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(postService, never()).createPost(any(), any());
    }

    @Test
    void createPost_withCaptionTooLong_returns400() throws Exception {
        CreatePostRequest req = CreatePostRequest.builder()
                .caption("a".repeat(2001))
                .build();

        mockMvc.perform(post("/api/v1/feed/v2")
                        .with(jwt().jwt(j -> j.subject(AUTHOR_UUID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── getPostById ─────────────────────────────────────────────────────

    @Test
    void getPostById_public_returns200() throws Exception {
        when(postService.getPostById(42L)).thenReturn(sampleDTO());

        mockMvc.perform(get("/api/v1/feed/v2/42").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(42))
                .andExpect(jsonPath("$.caption").value("hello world"));
    }

    @Test
    void getPostById_notFound_returns404() throws Exception {
        when(postService.getPostById(99L))
                .thenThrow(new GenericException("not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/feed/v2/99").with(anonymous()))
                .andExpect(status().isNotFound());
    }

    // ── deletePost ──────────────────────────────────────────────────────

    @Test
    void deletePost_asAuthor_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/feed/v2/42")
                        .with(jwt().jwt(j -> j.subject(AUTHOR_UUID))))
                .andExpect(status().isNoContent());

        verify(postService).deletePost(any(), eq(42L));
    }

    @Test
    void deletePost_asNonAuthor_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/feed/v2/42")
                        .with(jwt().jwt(j -> j.subject(OTHER_UUID))))
                .andExpect(status().isForbidden());

        verify(postService, never()).deletePost(any(), any());
    }

    @Test
    void deletePost_asAnonymous_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/feed/v2/42").with(anonymous()))
                .andExpect(status().isUnauthorized());

        verify(postService, never()).deletePost(any(), any());
    }

    @Test
    void deletePost_notFound_returns404() throws Exception {
        doThrow(new GenericException("not found", HttpStatus.NOT_FOUND))
                .when(postService).deletePost(any(), eq(99L));

        mockMvc.perform(delete("/api/v1/feed/v2/99")
                        .with(jwt().jwt(j -> j.subject(AUTHOR_UUID))))
                .andExpect(status().isNotFound());
    }

    // ── listFeed ────────────────────────────────────────────────────────

    @Test
    void listFeed_publicNoCursor_returnsPage() throws Exception {
        FeedPageDTO body = FeedPageDTO.builder()
                .items(List.of(sampleDTO()))
                .nextCursor("eyJ0cyI6Li4ufQ")
                .build();
        when(postService.listChronological(null, null)).thenReturn(body);

        mockMvc.perform(get("/api/v1/feed/v2").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].postId").value(42))
                .andExpect(jsonPath("$.nextCursor").value("eyJ0cyI6Li4ufQ"));
    }

    @Test
    void listFeed_passesCursorAndSizeThrough() throws Exception {
        FeedPageDTO body = FeedPageDTO.builder().items(List.of()).nextCursor(null).build();
        when(postService.listChronological("abc", 5)).thenReturn(body);

        mockMvc.perform(get("/api/v1/feed/v2")
                        .param("cursor", "abc")
                        .param("size", "5")
                        .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        verify(postService).listChronological("abc", 5);
    }

    @Test
    void listFeed_malformedCursor_returns400() throws Exception {
        when(postService.listChronological(eq("garbage"), any()))
                .thenThrow(new GenericException("Malformed cursor", HttpStatus.BAD_REQUEST));

        mockMvc.perform(get("/api/v1/feed/v2")
                        .param("cursor", "garbage")
                        .with(anonymous()))
                .andExpect(status().isBadRequest());
    }
}
