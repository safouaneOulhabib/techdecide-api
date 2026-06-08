package com.techdecide.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdecide.api.dto.comment.CommentDTO;
import com.techdecide.api.dto.comment.CreateCommentRequest;
import com.techdecide.api.entity.Comment;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.security.JwtService;
import com.techdecide.api.security.TestSecurityConfig;
import com.techdecide.api.security.UserDetailsServiceImpl;
import com.techdecide.api.service.CommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommentController.class)
@Import(TestSecurityConfig.class)
class CommentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CommentService commentService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private CommentDTO buildDTO() {
        return CommentDTO.builder()
                .id(1L).content("Great decision!").vote(Comment.Vote.APPROVE)
                .authorName("Alice").decisionId(1L)
                .createdAt(LocalDateTime.now()).build();
    }

    private CreateCommentRequest buildRequest() {
        CreateCommentRequest req = new CreateCommentRequest();
        req.setContent("Great decision!");
        req.setVote(Comment.Vote.APPROVE);
        return req;
    }

    // --- POST /api/decisions/{decisionId}/comments ---

    @Test
    @WithMockUser
    void create_validRequest_returns201() throws Exception {
        when(commentService.create(eq(1L), any(), anyString())).thenReturn(buildDTO());

        mockMvc.perform(post("/api/decisions/1/comments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("Great decision!"))
                .andExpect(jsonPath("$.vote").value("APPROVE"))
                .andExpect(jsonPath("$.authorName").value("Alice"));
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/decisions/1/comments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void create_missingContent_returns400() throws Exception {
        CreateCommentRequest req = new CreateCommentRequest();
        req.setVote(Comment.Vote.APPROVE);

        mockMvc.perform(post("/api/decisions/1/comments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.content").exists());

        verify(commentService, never()).create(any(), any(), any());
    }

    @Test
    @WithMockUser
    void create_blankContent_returns400() throws Exception {
        CreateCommentRequest req = new CreateCommentRequest();
        req.setContent("  ");

        mockMvc.perform(post("/api/decisions/1/comments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void create_decisionNotFound_returns404() throws Exception {
        when(commentService.create(eq(99L), any(), any()))
                .thenThrow(new ResourceNotFoundException("Decision", 99L));

        mockMvc.perform(post("/api/decisions/99/comments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Decision not found with id: 99"));
    }

    @Test
    @WithMockUser
    void create_withoutVote_returns201() throws Exception {
        CreateCommentRequest req = new CreateCommentRequest();
        req.setContent("Just a comment");
        CommentDTO dto = CommentDTO.builder().id(2L).content("Just a comment")
                .vote(null).authorName("Alice").decisionId(1L)
                .createdAt(LocalDateTime.now()).build();
        when(commentService.create(eq(1L), any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/decisions/1/comments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vote").doesNotExist());
    }

    // --- GET /api/decisions/{decisionId}/comments ---

    @Test
    @WithMockUser
    void getByDecision_existingDecision_returns200() throws Exception {
        when(commentService.getByDecision(1L)).thenReturn(List.of(buildDTO()));

        mockMvc.perform(get("/api/decisions/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Great decision!"))
                .andExpect(jsonPath("$[0].decisionId").value(1));
    }

    @Test
    void getByDecision_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/decisions/1/comments")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getByDecision_notFound_returns404() throws Exception {
        when(commentService.getByDecision(99L))
                .thenThrow(new ResourceNotFoundException("Decision", 99L));

        mockMvc.perform(get("/api/decisions/99/comments"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getByDecision_emptyList_returns200EmptyArray() throws Exception {
        when(commentService.getByDecision(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/decisions/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- DELETE /api/comments/{id} ---

    @Test
    @WithMockUser
    void delete_existingId_returns204() throws Exception {
        doNothing().when(commentService).delete(eq(1L), anyString());

        mockMvc.perform(delete("/api/comments/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(commentService).delete(eq(1L), anyString());
    }

    @Test
    @WithMockUser
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Comment", 99L)).when(commentService).delete(eq(99L), anyString());

        mockMvc.perform(delete("/api/comments/99").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void delete_notOwner_returns403() throws Exception {
        doThrow(new ForbiddenException("You are not the author of this comment"))
                .when(commentService).delete(eq(1L), anyString());

        mockMvc.perform(delete("/api/comments/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/comments/1").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
