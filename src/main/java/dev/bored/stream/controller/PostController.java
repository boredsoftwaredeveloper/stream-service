package dev.bored.stream.controller;

import dev.bored.stream.dto.CreatePostRequest;
import dev.bored.stream.dto.PostDTO;
import dev.bored.stream.service.PostService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for v2 posts.
 *
 * <p>Lives under {@code /api/v1/feed/v2} for the duration of coexistence
 * with the legacy {@code feed_post}-backed endpoints under
 * {@code /api/v1/feed}. The path {@code /v2} will be retired in a future
 * cleanup PR once the legacy code is dropped.</p>
 *
 * <p>Read endpoints are public. Write endpoints are gated to the
 * configured portfolio owner via {@code @PreAuthorize} comparing the
 * JWT {@code sub} to {@code @streamProperties.authorUserId}.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-25
 */
@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/feed/v2")
public class PostController {

    /** SpEL expression that says "the JWT subject is the configured author UUID". */
    private static final String AUTHOR_ONLY =
            "isAuthenticated() and authentication.name == @streamProperties.authorUserId.toString()";

    private final PostService postService;

    /**
     * Creates a new post. Author-only.
     *
     * @param jwt the validated JWT (injected by Spring Security)
     * @param req the post body
     * @return the created post, with author hydrated
     */
    @PostMapping
    @PreAuthorize(AUTHOR_ONLY)
    public ResponseEntity<PostDTO> createPost(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePostRequest req) {
        PostDTO created = postService.createPost(jwt, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Returns a single post by id. Public read.
     *
     * @param postId the post id
     * @return the post DTO; 404 if missing or soft-deleted
     */
    @GetMapping("/{postId}")
    public PostDTO getPostById(@PathVariable Long postId) {
        return postService.getPostById(postId);
    }

    /**
     * Soft-deletes a post by id. Author-only.
     *
     * @param jwt    the validated JWT
     * @param postId the post id
     */
    @DeleteMapping("/{postId}")
    @PreAuthorize(AUTHOR_ONLY)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long postId) {
        postService.deletePost(jwt, postId);
    }
}
