package dev.bored.stream.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bored.common.exception.GenericException;
import dev.bored.stream.config.SecurityConfig;
import dev.bored.stream.dto.CodeLineDTO;
import dev.bored.stream.dto.CodeSegmentDTO;
import dev.bored.stream.dto.CodeSnippetDTO;
import dev.bored.stream.dto.FeedPostDTO;
import dev.bored.stream.service.FeedPostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeedPostController.class)
@Import(SecurityConfig.class)
@WithMockUser
class FeedPostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FeedPostService feedPostService;

    private final FeedPostDTO testDTO = FeedPostDTO.builder()
            .postId(1L)
            .id("1")
            .author("sethi")
            .avatar("S")
            .timestamp("2h ago")
            .location("San Francisco, CA")
            .contentType("code")
            .codeSnippet(CodeSnippetDTO.builder()
                    .lines(List.of(CodeLineDTO.builder()
                            .segments(List.of(CodeSegmentDTO.builder()
                                    .text("const x = 1;")
                                    .type("plain")
                                    .build()))
                            .build()))
                    .build())
            .caption("Just another day")
            .hashtags(List.of("#code", "#life"))
            .sortOrder(0)
            .build();

    @Test
    void getAllPosts_ShouldReturnList() throws Exception {
        when(feedPostService.getAllPosts()).thenReturn(List.of(testDTO));

        mockMvc.perform(get("/api/v1/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].author").value("sethi"))
                .andExpect(jsonPath("$[0].contentType").value("code"));
    }

    @Test
    void getAllPosts_ShouldReturnEmpty_WhenNone() throws Exception {
        when(feedPostService.getAllPosts()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getPostById_ShouldReturnDTO() throws Exception {
        when(feedPostService.getPostById(1L)).thenReturn(testDTO);

        mockMvc.perform(get("/api/v1/feed/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("sethi"))
                .andExpect(jsonPath("$.caption").value("Just another day"));
    }

    @Test
    void getPostById_ShouldReturn404_WhenNotFound() throws Exception {
        when(feedPostService.getPostById(999L))
                .thenThrow(new GenericException("Feed post not found with id: 999", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/feed/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void addPost_ShouldReturnCreated() throws Exception {
        when(feedPostService.addPost(any(FeedPostDTO.class))).thenReturn(testDTO);

        mockMvc.perform(post("/api/v1/feed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("sethi"));
    }

    @Test
    void updatePost_ShouldReturnUpdated() throws Exception {
        when(feedPostService.updatePost(eq(1L), any(FeedPostDTO.class))).thenReturn(testDTO);

        mockMvc.perform(put("/api/v1/feed/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("sethi"));
    }

    @Test
    void deletePost_ShouldReturnTrue() throws Exception {
        when(feedPostService.deletePost(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/v1/feed/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void deletePost_ShouldReturn404_WhenNotFound() throws Exception {
        when(feedPostService.deletePost(999L))
                .thenThrow(new GenericException("Feed post not found with id: 999", HttpStatus.NOT_FOUND));

        mockMvc.perform(delete("/api/v1/feed/999"))
                .andExpect(status().isNotFound());
    }
}
