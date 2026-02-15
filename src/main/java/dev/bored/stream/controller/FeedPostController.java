package dev.bored.stream.controller;

import dev.bored.stream.dto.FeedPostDTO;
import dev.bored.stream.service.FeedPostService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the Regret Stream feed.
 *
 * <p>Exposes CRUD endpoints under {@code /api/v1/feed}.
 * GET operations are public; POST/PUT/DELETE require a valid Supabase JWT.</p>
 *
 * @author Bored Software Developer
 * @since 2026-02-15
 */
@RestController
@AllArgsConstructor
@RequestMapping("api/v1/feed")
public class FeedPostController {

    private final FeedPostService feedPostService;

    /**
     * Returns all feed posts ordered by sort order.
     *
     * @return list of {@link FeedPostDTO} objects
     */
    @GetMapping
    public List<FeedPostDTO> getAllPosts() {
        return feedPostService.getAllPosts();
    }

    /**
     * Returns a single feed post by its id.
     *
     * @param postId the unique identifier
     * @return the matching {@link FeedPostDTO}
     */
    @GetMapping("/{postId}")
    public FeedPostDTO getPostById(@PathVariable Long postId) {
        return feedPostService.getPostById(postId);
    }

    /**
     * Creates a new feed post.
     *
     * @param dto the post data to persist
     * @return the newly created {@link FeedPostDTO}
     */
    @PostMapping
    public FeedPostDTO addPost(@RequestBody FeedPostDTO dto) {
        return feedPostService.addPost(dto);
    }

    /**
     * Updates an existing feed post.
     *
     * @param postId the id of the post to update
     * @param dto    the updated post data
     * @return the updated {@link FeedPostDTO}
     */
    @PutMapping("/{postId}")
    public FeedPostDTO updatePost(@PathVariable Long postId, @RequestBody FeedPostDTO dto) {
        return feedPostService.updatePost(postId, dto);
    }

    /**
     * Deletes a feed post by its id.
     *
     * @param postId the id of the post to delete
     * @return {@code true} if successfully deleted
     */
    @DeleteMapping("/{postId}")
    public boolean deletePost(@PathVariable Long postId) {
        return feedPostService.deletePost(postId);
    }
}
