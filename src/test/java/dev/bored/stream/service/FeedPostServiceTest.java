package dev.bored.stream.service;

import dev.bored.common.exception.GenericException;
import dev.bored.stream.dto.FeedPostDTO;
import dev.bored.stream.entity.FeedPost;
import dev.bored.stream.mapper.FeedPostMapper;
import dev.bored.stream.repository.FeedPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedPostServiceTest {

    @Mock
    private FeedPostRepository feedPostRepository;

    @Mock
    private FeedPostMapper feedPostMapper;

    @InjectMocks
    private FeedPostService feedPostService;

    private FeedPost testEntity;
    private FeedPostDTO testDTO;

    @BeforeEach
    void setUp() {
        testEntity = new FeedPost();
        testEntity.setPostId(1L);
        testEntity.setAuthor("sethi");
        testEntity.setAvatar("S");
        testEntity.setTimestamp("2h ago");
        testEntity.setLocation("San Francisco, CA");
        testEntity.setContentType("code");
        testEntity.setCodeSnippet(Map.of("lines", List.of()));
        testEntity.setCaption("Just another day");
        testEntity.setHashtags(List.of("#code", "#life"));
        testEntity.setSortOrder(0);

        testDTO = FeedPostDTO.builder()
                .postId(1L)
                .id("1")
                .author("sethi")
                .avatar("S")
                .timestamp("2h ago")
                .location("San Francisco, CA")
                .contentType("code")
                .caption("Just another day")
                .hashtags(List.of("#code", "#life"))
                .sortOrder(0)
                .build();
    }

    @Test
    void getAllPosts_ShouldReturnList() {
        when(feedPostRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(testEntity));
        when(feedPostMapper.toDTOList(List.of(testEntity))).thenReturn(List.of(testDTO));

        List<FeedPostDTO> result = feedPostService.getAllPosts();
        assertEquals(1, result.size());
        assertEquals("sethi", result.get(0).getAuthor());
    }

    @Test
    void getAllPosts_ShouldReturnEmpty_WhenNone() {
        when(feedPostRepository.findAllByOrderBySortOrderAsc()).thenReturn(Collections.emptyList());
        when(feedPostMapper.toDTOList(Collections.emptyList())).thenReturn(Collections.emptyList());

        assertTrue(feedPostService.getAllPosts().isEmpty());
    }

    @Test
    void getPostById_ShouldReturnDTO() {
        when(feedPostRepository.findById(1L)).thenReturn(Optional.of(testEntity));
        when(feedPostMapper.toDTO(testEntity)).thenReturn(testDTO);

        assertEquals("sethi", feedPostService.getPostById(1L).getAuthor());
    }

    @Test
    void getPostById_ShouldThrow_WhenNotFound() {
        when(feedPostRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(GenericException.class, () -> feedPostService.getPostById(999L));
    }

    @Test
    void addPost_ShouldReturnSavedDTO() {
        when(feedPostMapper.toEntity(testDTO)).thenReturn(testEntity);
        when(feedPostRepository.save(testEntity)).thenReturn(testEntity);
        when(feedPostMapper.toDTO(testEntity)).thenReturn(testDTO);

        assertEquals("sethi", feedPostService.addPost(testDTO).getAuthor());
    }

    @Test
    void updatePost_ShouldReturnUpdatedDTO() {
        when(feedPostRepository.findById(1L)).thenReturn(Optional.of(testEntity));
        when(feedPostMapper.codeSnippetDTOToMap(any())).thenReturn(null);
        when(feedPostMapper.imageContentDTOToMap(any())).thenReturn(null);
        when(feedPostRepository.save(testEntity)).thenReturn(testEntity);
        when(feedPostMapper.toDTO(testEntity)).thenReturn(testDTO);

        assertEquals("sethi", feedPostService.updatePost(1L, testDTO).getAuthor());
    }

    @Test
    void updatePost_ShouldThrow_WhenNotFound() {
        when(feedPostRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(GenericException.class, () -> feedPostService.updatePost(999L, testDTO));
        verify(feedPostRepository, never()).save(any());
    }

    @Test
    void deletePost_ShouldReturnTrue() {
        when(feedPostRepository.existsById(1L)).thenReturn(true);
        assertTrue(feedPostService.deletePost(1L));
        verify(feedPostRepository).deleteById(1L);
    }

    @Test
    void deletePost_ShouldThrow_WhenNotFound() {
        when(feedPostRepository.existsById(999L)).thenReturn(false);
        assertThrows(GenericException.class, () -> feedPostService.deletePost(999L));
    }
}
