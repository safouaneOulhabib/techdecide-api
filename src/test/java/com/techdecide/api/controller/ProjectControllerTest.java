package com.techdecide.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdecide.api.dto.project.AssignTeamRequest;
import com.techdecide.api.dto.project.CreateProjectRequest;
import com.techdecide.api.dto.project.ProjectDTO;
import com.techdecide.api.dto.project.ProjectTeamDTO;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.security.JwtService;
import com.techdecide.api.security.TestSecurityConfig;
import com.techdecide.api.security.UserDetailsServiceImpl;
import com.techdecide.api.service.ProjectService;
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

@WebMvcTest(ProjectController.class)
@Import(TestSecurityConfig.class)
class ProjectControllerTest {

    // QA matrix coverage:
    // SEC-01 PROJ-03 PROJ-04 PROJ-13

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ProjectService projectService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private ProjectDTO buildDTO() {
        return ProjectDTO.builder()
                .id(1L).name("GTN").description("GTN project")
                .organizationId(10L).organizationName("Acme Corp")
                .teamCount(2)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private CreateProjectRequest buildCreateRequest() {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setName("GTN");
        req.setDescription("GTN project");
        req.setOrganizationId(10L);
        return req;
    }

    private ProjectTeamDTO buildTeamDTO() {
        return ProjectTeamDTO.builder().teamId(1L).teamName("Backend Team").build();
    }

    // ── POST /api/projects ───────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void create_validRequest_returns201() throws Exception {
        when(projectService.create(any(), anyString())).thenReturn(buildDTO());

        mockMvc.perform(post("/api/projects").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("GTN"));
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/projects").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void create_missingName_returns400() throws Exception {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setOrganizationId(10L);

        mockMvc.perform(post("/api/projects").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).create(any(), any());
    }

    @Test
    @WithMockUser
    void create_forbidden_returns403() throws Exception {
        when(projectService.create(any(), anyString()))
                .thenThrow(new ForbiddenException("Only APP_ADMIN can perform this action"));

        mockMvc.perform(post("/api/projects").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreateRequest())))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/projects ────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void getAll_returns200WithList() throws Exception {
        when(projectService.getAll(anyString())).thenReturn(List.of(buildDTO()));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("GTN"));
    }

    @Test
    void getAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }

    // ── GET /api/projects/:id ────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void getById_existingId_returns200() throws Exception {
        when(projectService.getById(eq(1L), anyString())).thenReturn(buildDTO());

        mockMvc.perform(get("/api/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("GTN"));
    }

    @Test
    @WithMockUser
    void getById_notFound_returns404() throws Exception {
        when(projectService.getById(eq(99L), anyString()))
                .thenThrow(new ResourceNotFoundException("Project", 99L));

        mockMvc.perform(get("/api/projects/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getById_forbidden_returns403() throws Exception {
        when(projectService.getById(eq(1L), anyString()))
                .thenThrow(new ForbiddenException("Access denied"));

        mockMvc.perform(get("/api/projects/1"))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/projects/:id ─────────────────────────────────────────────────

    @Test
    @WithMockUser
    void delete_existingId_returns204() throws Exception {
        doNothing().when(projectService).delete(eq(1L), anyString());

        mockMvc.perform(delete("/api/projects/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(projectService).delete(eq(1L), anyString());
    }

    @Test
    @WithMockUser
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Project", 99L))
                .when(projectService).delete(eq(99L), anyString());

        mockMvc.perform(delete("/api/projects/99").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void delete_forbidden_returns403() throws Exception {
        doThrow(new ForbiddenException("Only APP_ADMIN can perform this action"))
                .when(projectService).delete(eq(1L), anyString());

        mockMvc.perform(delete("/api/projects/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/projects/:id/teams ──────────────────────────────────────────────

    @Test
    @WithMockUser
    void getTeams_returns200() throws Exception {
        when(projectService.getTeams(eq(1L), anyString())).thenReturn(List.of(buildTeamDTO()));

        mockMvc.perform(get("/api/projects/1/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamId").value(1))
                .andExpect(jsonPath("$[0].teamName").value("Backend Team"));
    }

    // ── GET /api/projects/:id/available-teams ────────────────────────────────────

    @Test
    @WithMockUser
    void getAvailableTeams_returns200() throws Exception {
        when(projectService.getAvailableTeams(eq(1L), anyString())).thenReturn(List.of(buildTeamDTO()));

        mockMvc.perform(get("/api/projects/1/available-teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamName").value("Backend Team"));
    }

    @Test
    @WithMockUser
    void getAvailableTeams_forbidden_returns403() throws Exception {
        when(projectService.getAvailableTeams(eq(1L), anyString()))
                .thenThrow(new ForbiddenException("Only APP_ADMIN can perform this action"));

        mockMvc.perform(get("/api/projects/1/available-teams"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/projects/:id/teams ─────────────────────────────────────────────

    @Test
    @WithMockUser
    void assignTeam_validRequest_returns201() throws Exception {
        AssignTeamRequest req = new AssignTeamRequest(1L);
        when(projectService.assignTeam(eq(1L), eq(1L), anyString())).thenReturn(buildTeamDTO());

        mockMvc.perform(post("/api/projects/1/teams").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").value(1));
    }

    @Test
    @WithMockUser
    void assignTeam_conflict_returns409() throws Exception {
        AssignTeamRequest req = new AssignTeamRequest(1L);
        when(projectService.assignTeam(eq(1L), eq(1L), anyString()))
                .thenThrow(new ConflictException("Team is already assigned to this project"));

        mockMvc.perform(post("/api/projects/1/teams").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void assignTeam_forbidden_returns403() throws Exception {
        AssignTeamRequest req = new AssignTeamRequest(1L);
        when(projectService.assignTeam(eq(1L), eq(1L), anyString()))
                .thenThrow(new ForbiddenException("Only APP_ADMIN can perform this action"));

        mockMvc.perform(post("/api/projects/1/teams").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/projects/:id/teams/:teamId ───────────────────────────────────

    @Test
    @WithMockUser
    void removeTeam_returns204() throws Exception {
        doNothing().when(projectService).removeTeam(eq(1L), eq(1L), anyString());

        mockMvc.perform(delete("/api/projects/1/teams/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(projectService).removeTeam(eq(1L), eq(1L), anyString());
    }

    @Test
    @WithMockUser
    void removeTeam_forbidden_returns403() throws Exception {
        doThrow(new ForbiddenException("Only APP_ADMIN can perform this action"))
                .when(projectService).removeTeam(eq(1L), eq(1L), anyString());

        mockMvc.perform(delete("/api/projects/1/teams/1").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
