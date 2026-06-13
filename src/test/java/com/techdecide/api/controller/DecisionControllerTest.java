package com.techdecide.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdecide.api.dto.decision.CreateDecisionRequest;
import com.techdecide.api.dto.decision.DecisionDTO;
import com.techdecide.api.dto.decision.UpdateDecisionRequest;
import com.techdecide.api.dto.tag.TagDTO;
import com.techdecide.api.entity.Decision;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.security.JwtService;
import com.techdecide.api.security.TestSecurityConfig;
import com.techdecide.api.security.UserDetailsServiceImpl;
import com.techdecide.api.service.DecisionService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DecisionController.class)
@Import(TestSecurityConfig.class)
class DecisionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean DecisionService decisionService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private DecisionDTO buildDTO() {
        return DecisionDTO.builder()
                .id(1L).title("Use PostgreSQL").context("We need a DB")
                .decision("PostgreSQL chosen").consequences("Cost implications")
                .status(Decision.Status.DRAFT)
                .authorName("Alice").teamName("Engineering")
                .tags(List.of(TagDTO.builder().id(1L).name("backend").color("#ff0000").build())).alternatives(List.of())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private CreateDecisionRequest buildCreateRequest() {
        CreateDecisionRequest req = new CreateDecisionRequest();
        req.setTitle("Use PostgreSQL");
        req.setContext("We need a DB");
        req.setDecision("PostgreSQL chosen");
        req.setTeamId(1L);
        return req;
    }

    // --- POST /api/decisions ---

    @Test
    @WithMockUser
    void create_authenticatedValidRequest_returns201() throws Exception {
        when(decisionService.create(any(), anyString())).thenReturn(buildDTO());

        mockMvc.perform(post("/api/decisions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Use PostgreSQL"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/decisions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void create_missingTitle_returns400() throws Exception {
        CreateDecisionRequest req = buildCreateRequest();
        req.setTitle(null);

        mockMvc.perform(post("/api/decisions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").exists());

        verify(decisionService, never()).create(any(), any());
    }

    @Test
    @WithMockUser
    void create_missingContext_returns400() throws Exception {
        CreateDecisionRequest req = buildCreateRequest();
        req.setContext(null);

        mockMvc.perform(post("/api/decisions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void create_missingTeamId_returns400() throws Exception {
        CreateDecisionRequest req = buildCreateRequest();
        req.setTeamId(null);

        mockMvc.perform(post("/api/decisions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // --- GET /api/decisions ---

    @Test
    @WithMockUser
    void getAll_authenticated_returns200WithList() throws Exception {
        when(decisionService.getAll(anyString())).thenReturn(List.of(buildDTO()));

        mockMvc.perform(get("/api/decisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].title").value("Use PostgreSQL"));
    }

    @Test
    void getAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/decisions")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getAll_emptyList_returns200WithEmptyArray() throws Exception {
        when(decisionService.getAll(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/decisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- GET /api/decisions/{id} ---

    @Test
    @WithMockUser
    void getById_existingId_returns200() throws Exception {
        when(decisionService.getById(eq(1L), anyString())).thenReturn(buildDTO());

        mockMvc.perform(get("/api/decisions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser
    void getById_nonExistingId_returns404() throws Exception {
        when(decisionService.getById(eq(99L), anyString()))
                .thenThrow(new ResourceNotFoundException("Decision", 99L));

        mockMvc.perform(get("/api/decisions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Decision not found with id: 99"));
    }

    // --- GET /api/decisions/team/{teamId} ---

    @Test
    @WithMockUser
    void getByTeam_returns200WithList() throws Exception {
        when(decisionService.getByTeam(1L)).thenReturn(List.of(buildDTO()));

        mockMvc.perform(get("/api/decisions/team/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamName").value("Engineering"));
    }

    // --- GET /api/decisions/search ---

    @Test
    @WithMockUser
    void search_returns200WithResults() throws Exception {
        when(decisionService.search("postgres")).thenReturn(List.of(buildDTO()));

        mockMvc.perform(get("/api/decisions/search").param("keyword", "postgres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Use PostgreSQL"));
    }

    // --- PUT /api/decisions/{id} ---

    @Test
    @WithMockUser
    void update_validRequest_returns200() throws Exception {
        UpdateDecisionRequest req = new UpdateDecisionRequest();
        req.setTitle("Updated Title");
        DecisionDTO updated = buildDTO();
        updated.setTitle("Updated Title");
        when(decisionService.update(eq(1L), any(), anyString())).thenReturn(updated);

        mockMvc.perform(put("/api/decisions/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    @WithMockUser
    void update_notFound_returns404() throws Exception {
        when(decisionService.update(eq(99L), any(), anyString()))
                .thenThrow(new ResourceNotFoundException("Decision", 99L));

        mockMvc.perform(put("/api/decisions/99").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateDecisionRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/decisions/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateDecisionRequest())))
                .andExpect(status().isUnauthorized());
    }

    // --- PATCH /api/decisions/{id}/status ---

    @Test
    @WithMockUser
    void updateStatus_validStatus_returns200() throws Exception {
        DecisionDTO proposed = buildDTO();
        proposed.setStatus(Decision.Status.PROPOSED);
        when(decisionService.updateStatus(eq(1L), eq(Decision.Status.PROPOSED), isNull(), anyString())).thenReturn(proposed);

        mockMvc.perform(patch("/api/decisions/1/status").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PROPOSED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROPOSED"));
    }

    // --- DELETE /api/decisions/{id} ---

    @Test
    @WithMockUser
    void delete_existingId_returns204() throws Exception {
        doNothing().when(decisionService).delete(eq(1L), anyString());

        mockMvc.perform(delete("/api/decisions/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(decisionService).delete(eq(1L), anyString());
    }

    @Test
    @WithMockUser
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Decision", 99L)).when(decisionService).delete(eq(99L), anyString());

        mockMvc.perform(delete("/api/decisions/99").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/decisions/1").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
