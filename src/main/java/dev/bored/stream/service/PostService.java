package dev.bored.stream.service;

import dev.bored.common.exception.GenericException;
import dev.bored.stream.dto.CreatePostRequest;
import dev.bored.stream.dto.FeedPageDTO;
import dev.bored.stream.dto.PostDTO;
import dev.bored.stream.entity.AppUser;
import dev.bored.stream.entity.Post;
import dev.bored.stream.mapper.PostMapper;
import dev.bored.stream.repository.AppUserRepository;
import dev.bored.stream.repository.PostRepository;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /** Feed-list size bounds. Default 20, hard cap 100. */
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

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

    /**
     * Returns one page of the chronological feed (newest first), using
     * keyset pagination.
     *
     * <p><b>Why keyset and not offset?</b> Classic offset pagination
     * ({@code LIMIT 20 OFFSET 9000}) makes Postgres count and discard
     * 9,000 rows on every page-9001 request. Page cost grows linearly
     * with how deep the user scrolls. Keyset pagination, by contrast,
     * uses the index to seek directly to the cursor boundary — page
     * cost stays {@code O(size)} no matter how deep we are.</p>
     *
     * <p><b>The +1 trick:</b> we ask the DB for {@code size + 1} rows,
     * keep the first {@code size}, and use the existence of the extra
     * row as our {@code hasMore} signal. This is one query instead of
     * two (the alternative being a {@code SELECT COUNT(*)} alongside
     * the page query, which would walk every live row).</p>
     *
     * <p><b>Author hydration:</b> after fetching the page we collect
     * the distinct author ids and load them in a single
     * {@code findAllById} call. For a single-author site that's one
     * extra row per page — trivial. The pattern generalises if we ever
     * relax the single-author rule.</p>
     *
     * @param cursorToken opaque cursor from the previous page, or null/blank for the first
     * @param requestedSize page size; clamped to {@code [1, MAX_PAGE_SIZE]}; null/non-positive → default
     * @return one page of posts plus a {@code nextCursor} when more exist
     */
    @Transactional(readOnly = true)
    public FeedPageDTO listChronological(String cursorToken, Integer requestedSize) {
        int size = clampSize(requestedSize);
        // The +1 trick: ask for one more than we'll return so we can
        // detect "hasMore" without a separate COUNT query.
        int fetchSize = size + 1;

        List<Post> rows = (cursorToken == null || cursorToken.isBlank())
                ? postRepository.findChronoFirstPage(PageRequest.of(0, fetchSize))
                : fetchAfterCursor(cursorToken, fetchSize);

        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = rows.subList(0, size);
        }

        if (rows.isEmpty()) {
            return FeedPageDTO.builder().items(List.of()).nextCursor(null).build();
        }

        // Batch-load authors in a single query, then map per row.
        Set<UUID> authorIds = rows.stream()
                .map(Post::getAuthorId)
                .collect(Collectors.toSet());
        Map<UUID, AppUser> authors = appUserRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(AppUser::getUserId, Function.identity()));

        List<PostDTO> items = rows.stream()
                .map(p -> postMapper.toDTO(p, authors.get(p.getAuthorId())))
                .toList();

        // Cursor for the next page is the (created_at, post_id) of the
        // LAST row on this page — that's the boundary the next request
        // will sit strictly below.
        String nextCursor = null;
        if (hasMore) {
            Post last = rows.get(rows.size() - 1);
            nextCursor = FeedCursor.encode(last.getCreatedAt(), last.getPostId());
        }

        return FeedPageDTO.builder().items(items).nextCursor(nextCursor).build();
    }

    /** Decodes the cursor and runs the keyset query. */
    private List<Post> fetchAfterCursor(String cursorToken, int fetchSize) {
        FeedCursor cursor = FeedCursor.decode(cursorToken);
        return postRepository.findChronoPageAfter(
                cursor.ts(), cursor.postId(), PageRequest.of(0, fetchSize));
    }

    /**
     * Bounds the requested page size to {@code [1, MAX_PAGE_SIZE]}.
     * {@code null} or non-positive means "give me the default."
     */
    private static int clampSize(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(requested, MAX_PAGE_SIZE);
    }
}
