package dev.bored.stream.service;

import dev.bored.common.exception.GenericException;
import dev.bored.stream.dto.CreatePostRequest;
import dev.bored.stream.dto.PostDTO;
import dev.bored.stream.entity.AppUser;
import dev.bored.stream.entity.Post;
import dev.bored.stream.mapper.PostMapper;
import dev.bored.stream.repository.AppUserRepository;
import dev.bored.stream.repository.PostRepository;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Business logic for {@code post} CRUD.
 *
 * <p>Author-only writes: the controller enforces "only the configured
 * portfolio owner may POST/DELETE" via {@code @PreAuthorize} comparing
 * the JWT {@code sub} to {@link dev.bored.stream.config.StreamProperties#getAuthorUserId()}.
 * The service trusts that gate and writes the JWT subject straight into
 * {@code post.authorId}.</p>
 *
 * <p>Reads go through {@code @Cacheable(POST_BY_ID)} with the project's
 * default 1-day TTL. Writes evict the affected key. The cached value is
 * the fully hydrated {@link PostDTO}, including denormalised author
 * fields — that means a display-name change won't show up on this post
 * until the cache TTL expires; acceptable for a single-author site
 * where the author updates their handle approximately never.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-25
 */
@Service
@AllArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final AppUserRepository appUserRepository;
    private final PostMapper postMapper;

    /**
     * Creates a new post on behalf of the authenticated author.
     *
     * <p>The controller has already gated this endpoint to the configured
     * author UUID via {@code @PreAuthorize}, so we don't re-check here —
     * we just trust {@code jwt.getSubject()} and persist.</p>
     *
     * @param jwt the validated Supabase JWT of the requester
     * @param req the validated post body
     * @return the persisted post, hydrated with author info
     */
    @Transactional
    public PostDTO createPost(Jwt jwt, CreatePostRequest req) {
        UUID authorId = UUID.fromString(jwt.getSubject());

        Post entity = postMapper.toEntity(req);
        entity.setAuthorId(authorId);

        Post saved = postRepository.save(entity);
        AppUser author = loadAuthor(authorId);
        return postMapper.toDTO(saved, author);
    }

    /**
     * Returns a single live post by id, with author hydrated.
     *
     * @param postId the post id
     * @return the hydrated DTO
     * @throws GenericException 404 if the post is missing or soft-deleted
     */
    @Cacheable(value = CacheNames.POST_BY_ID, key = "#postId")
    @Transactional(readOnly = true)
    public PostDTO getPostById(Long postId) {
        Post post = postRepository.findByPostIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new GenericException(
                        "Post not found with id: " + postId, HttpStatus.NOT_FOUND));
        AppUser author = loadAuthor(post.getAuthorId());
        return postMapper.toDTO(post, author);
    }

    /**
     * Soft-deletes a post by stamping {@code deleted_at}. The row is
     * preserved for audit / undelete; the partial keyset index drops it
     * out of feed reads instantly.
     *
     * @param jwt    the validated Supabase JWT of the requester
     * @param postId the post id
     * @throws GenericException 404 if the post is missing or already soft-deleted
     */
    @Transactional
    @CacheEvict(value = CacheNames.POST_BY_ID, key = "#postId")
    public void deletePost(Jwt jwt, Long postId) {
        Post post = postRepository.findByPostIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new GenericException(
                        "Post not found with id: " + postId, HttpStatus.NOT_FOUND));
        post.setDeletedAt(Instant.now());
        postRepository.save(post);
    }

    /**
     * Loads the author row for a post. The author is the configured
     * portfolio owner, mirrored into {@code app_user} by the user-sync
     * interceptor on every authenticated request — so the row is
     * effectively always present. If somehow it isn't, we return a 500
     * rather than fabricating a placeholder author.
     */
    private AppUser loadAuthor(UUID authorId) {
        return appUserRepository.findById(authorId)
                .orElseThrow(() -> new GenericException(
                        "Author row missing for user_id " + authorId
                                + " — did the user-sync interceptor fire?",
                        HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
