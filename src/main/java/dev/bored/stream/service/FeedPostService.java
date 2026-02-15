package dev.bored.stream.service;

import dev.bored.common.exception.GenericException;
import dev.bored.stream.dto.FeedPostDTO;
import dev.bored.stream.entity.FeedPost;
import dev.bored.stream.mapper.FeedPostMapper;
import dev.bored.stream.repository.FeedPostRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing {@link FeedPost} entities in the Regret Stream.
 *
 * <p>Provides full CRUD operations. All mutating methods run inside a
 * transactional context. Read-only methods use a read-only transaction
 * to allow potential database optimisations.</p>
 *
 * @author Bored Software Developer
 * @since 2026-02-15
 */
@Service
@AllArgsConstructor
public class FeedPostService {

    private final FeedPostRepository feedPostRepository;
    private final FeedPostMapper feedPostMapper;

    /**
     * Retrieves every feed post, ordered by {@code sort_order} ascending.
     *
     * @return an ordered list of {@link FeedPostDTO} objects
     */
    @Transactional(readOnly = true)
    public List<FeedPostDTO> getAllPosts() {
        return feedPostMapper.toDTOList(feedPostRepository.findAllByOrderBySortOrderAsc());
    }

    /**
     * Retrieves a single feed post by its primary key.
     *
     * @param postId the unique identifier of the post
     * @return the corresponding {@link FeedPostDTO}
     * @throws GenericException if no post exists with the given id (HTTP 404)
     */
    @Transactional(readOnly = true)
    public FeedPostDTO getPostById(Long postId) {
        FeedPost entity = feedPostRepository.findById(postId)
                .orElseThrow(() -> new GenericException(
                        "Feed post not found with id: " + postId, HttpStatus.NOT_FOUND));
        return feedPostMapper.toDTO(entity);
    }

    /**
     * Creates a new feed post.
     *
     * @param dto the data to persist
     * @return the newly created {@link FeedPostDTO} with its generated id
     */
    @Transactional
    public FeedPostDTO addPost(FeedPostDTO dto) {
        FeedPost entity = feedPostMapper.toEntity(dto);
        return feedPostMapper.toDTO(feedPostRepository.save(entity));
    }

    /**
     * Updates an existing feed post with the supplied data.
     *
     * @param postId the id of the post to update
     * @param dto    the updated data
     * @return the updated {@link FeedPostDTO}
     * @throws GenericException if no post exists with the given id (HTTP 404)
     */
    @Transactional
    public FeedPostDTO updatePost(Long postId, FeedPostDTO dto) {
        FeedPost existing = feedPostRepository.findById(postId)
                .orElseThrow(() -> new GenericException(
                        "Feed post not found with id: " + postId, HttpStatus.NOT_FOUND));

        existing.setAuthor(dto.getAuthor());
        existing.setAvatar(dto.getAvatar());
        existing.setTimestamp(dto.getTimestamp());
        existing.setLocation(dto.getLocation());
        existing.setContentType(dto.getContentType());
        existing.setCodeSnippet(feedPostMapper.codeSnippetDTOToMap(dto.getCodeSnippet()));
        existing.setImageContent(feedPostMapper.imageContentDTOToMap(dto.getImageContent()));
        existing.setCaption(dto.getCaption());
        existing.setHashtags(dto.getHashtags());
        existing.setSortOrder(dto.getSortOrder());

        return feedPostMapper.toDTO(feedPostRepository.save(existing));
    }

    /**
     * Deletes a feed post by its primary key.
     *
     * @param postId the id of the post to delete
     * @return {@code true} if the post was successfully deleted
     * @throws GenericException if no post exists with the given id (HTTP 404)
     */
    @Transactional
    public boolean deletePost(Long postId) {
        if (feedPostRepository.existsById(postId)) {
            feedPostRepository.deleteById(postId);
            return true;
        }
        throw new GenericException("Feed post not found with id: " + postId, HttpStatus.NOT_FOUND);
    }
}
