package com.techdecide.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdecide.api.dto.team.CreateTeamRequest;
import com.techdecide.api.dto.team.TeamDTO;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.security.JwtService;
import com.techdecide.api.security.TestSecurityConfig;
import com.techdecide.api.security.UserDetailsServiceImpl;
import com.techdecide.api.service.TeamService;
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

@WebMvcTest(TeamController.class)
@Import(TestSecurityConfig.class)
class TeamControllerTest {
    // QA matrix coverage:
    // TEAM-01 TEAM-02 TEAM-03 TEAM-04 TEAM-06 SEC-01

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean TeamService teamService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private TeamDTO buildDTO() {
        return TeamDTO.builder()
                .id(1L).name("Engineering").organizationId(1L)
                .organizationName("Acme").createdAt(LocalDateTime.now()).build();
    }

    private CreateTeamRequest buildRequest() {
        CreateTeamRequest req = new CreateTeamRequest();
        req.setName("Engineering");
        req.setOrganizationId(1L);
        return req;
    }

    // --- POST /api/teams ---

    @Test
    @WithMockUser
    void create_validRequest_returns201() throws Exception {
        when(teamService.create(any())).thenReturn(buildDTO());

        mockMvc.perform(post("/api/teams").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Engineering"))
                .andExpect(jsonPath("$.organizationName").value("Acme"));
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/teams").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void create_missingName_returns400() throws Exception {
        CreateTeamRequest req = new CreateTeamRequest();
        req.setOrganizationId(1L);

        mockMvc.perform(post("/api/teams").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());

        verify(teamService, never()).create(any());
    }

    @Test
    @WithMockUser
    void create_missingOrganizationId_returns400() throws Exception {
        CreateTeamRequest req = new CreateTeamRequest();
        req.setName("Engineering");

        mockMvc.perform(post("/api/teams").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.organizationId").exists());
    }

    @Test
    @WithMockUser
    void create_duplicateName_returns409() throws Exception {
        when(teamService.create(any()))
                .thenThrow(new ConflictException("Team with name 'Engineering' already exists in this organization"));

        mockMvc.perform(post("/api/teams").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isConflict());
    }

    // --- GET /api/teams ---

    @Test
    @WithMockUser
    void getAll_returns200WithList() throws Exception {
        when(teamService.getAll(anyString())).thenReturn(List.of(buildDTO()));

        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Engineering"));
    }

    @Test
    void getAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/teams")).andExpect(status().isUnauthorized());
    }

    // --- GET /api/teams/organization/{organizationId} ---

    @Test
    @WithMockUser
    void getByOrganization_returns200WithList() throws Exception {
        when(teamService.getByOrganization(1L)).thenReturn(List.of(buildDTO()));

        mockMvc.perform(get("/api/teams/organization/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].organizationId").value(1));
    }

    @Test
    @WithMockUser
    void getByOrganization_emptyList_returns200EmptyArray() throws Exception {
        when(teamService.getByOrganization(99L)).thenReturn(List.of());

        mockMvc.perform(get("/api/teams/organization/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- GET /api/teams/{id} ---

    @Test
    @WithMockUser
    void getById_existingId_returns200() throws Exception {
        when(teamService.getById(1L)).thenReturn(buildDTO());

        mockMvc.perform(get("/api/teams/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser
    void getById_notFound_returns404() throws Exception {
        when(teamService.getById(99L)).thenThrow(new ResourceNotFoundException("Team", 99L));

        mockMvc.perform(get("/api/teams/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Team not found with id: 99"));
    }

    // --- PUT /api/teams/{id} ---

    @Test
    @WithMockUser
    void update_validRequest_returns200() throws Exception {
        when(teamService.update(eq(1L), any())).thenReturn(buildDTO());

        mockMvc.perform(put("/api/teams/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Engineering"));
    }

    @Test
    @WithMockUser
    void update_notFound_returns404() throws Exception {
        when(teamService.update(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Team", 99L));

        mockMvc.perform(put("/api/teams/99").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/teams/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isUnauthorized());
    }

    // --- DELETE /api/teams/{id} ---

    @Test
    @WithMockUser
    void delete_existingId_returns204() throws Exception {
        doNothing().when(teamService).delete(1L);

        mockMvc.perform(delete("/api/teams/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(teamService).delete(1L);
    }

    @Test
    @WithMockUser
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Team", 99L)).when(teamService).delete(99L);

        mockMvc.perform(delete("/api/teams/99").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/teams/1").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
