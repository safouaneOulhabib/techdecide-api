package com.techdecide.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdecide.api.dto.team.AssignMemberRequest;
import com.techdecide.api.dto.team.AvailableUserDTO;
import com.techdecide.api.dto.team.ChangeRoleRequest;
import com.techdecide.api.dto.team.TeamMemberDTO;
import com.techdecide.api.exception.BadRequestException;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.security.JwtService;
import com.techdecide.api.security.TestSecurityConfig;
import com.techdecide.api.security.UserDetailsServiceImpl;
import com.techdecide.api.service.TeamMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TeamMemberController.class)
@Import(TestSecurityConfig.class)
class TeamMemberControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean TeamMemberService teamMemberService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private TeamMemberDTO buildDTO() {
        return TeamMemberDTO.builder()
                .userId(2L).name("Alice").email("alice@example.com")
                .teamRole("MEMBER").teamId(1L).build();
    }

    // --- GET /api/teams/{teamId}/available-users ---

    @Test
    @WithMockUser
    void getAvailableUsers_authenticated_returns200() throws Exception {
        AvailableUserDTO dto = AvailableUserDTO.builder()
                .id(3L).name("Bob").email("bob@example.com").build();
        when(teamMemberService.getAvailableUsers(eq(1L), anyString())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/teams/1/available-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].name").value("Bob"))
                .andExpect(jsonPath("$[0].email").value("bob@example.com"));
    }

    @Test
    @WithMockUser
    void getAvailableUsers_teamNotFound_returns404() throws Exception {
        when(teamMemberService.getAvailableUsers(eq(99L), anyString()))
                .thenThrow(new ResourceNotFoundException("Team", 99L));

        mockMvc.perform(get("/api/teams/99/available-users"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getAvailableUsers_memberForbidden_returns403() throws Exception {
        when(teamMemberService.getAvailableUsers(eq(1L), anyString()))
                .thenThrow(new ForbiddenException("Only APP_ADMIN or TEAM_ADMIN can manage team members"));

        mockMvc.perform(get("/api/teams/1/available-users"))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/teams/{teamId}/members ---

    @Test
    @WithMockUser
    void getMembers_authenticated_returns200() throws Exception {
        when(teamMemberService.getMembers(eq(1L), anyString())).thenReturn(List.of(buildDTO()));

        mockMvc.perform(get("/api/teams/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(2))
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[0].teamRole").value("MEMBER"));
    }

    @Test
    void getMembers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/teams/1/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getMembers_forbidden_returns403() throws Exception {
        when(teamMemberService.getMembers(eq(1L), anyString()))
                .thenThrow(new ForbiddenException("Only APP_ADMIN or TEAM_ADMIN can manage team members"));

        mockMvc.perform(get("/api/teams/1/members"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getMembers_teamNotFound_returns404() throws Exception {
        when(teamMemberService.getMembers(eq(99L), anyString()))
                .thenThrow(new ResourceNotFoundException("Team", 99L));

        mockMvc.perform(get("/api/teams/99/members"))
                .andExpect(status().isNotFound());
    }

    // --- POST /api/teams/{teamId}/members ---

    @Test
    @WithMockUser
    void assignMember_validRequest_returns201() throws Exception {
        AssignMemberRequest req = new AssignMemberRequest();
        req.setUserId(2L);
        when(teamMemberService.assignMember(eq(1L), any(), anyString())).thenReturn(buildDTO());

        mockMvc.perform(post("/api/teams/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(2));
    }

    @Test
    void assignMember_unauthenticated_returns401() throws Exception {
        AssignMemberRequest req = new AssignMemberRequest();
        req.setUserId(2L);

        mockMvc.perform(post("/api/teams/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void assignMember_missingUserId_returns400() throws Exception {
        AssignMemberRequest req = new AssignMemberRequest();

        mockMvc.perform(post("/api/teams/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(teamMemberService, never()).assignMember(any(), any(), any());
    }

    @Test
    @WithMockUser
    void assignMember_forbidden_returns403() throws Exception {
        AssignMemberRequest req = new AssignMemberRequest();
        req.setUserId(2L);
        when(teamMemberService.assignMember(eq(1L), any(), anyString()))
                .thenThrow(new ForbiddenException("Only APP_ADMIN or TEAM_ADMIN can manage team members"));

        mockMvc.perform(post("/api/teams/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // --- DELETE /api/teams/{teamId}/members/{userId} ---

    @Test
    @WithMockUser
    void removeMember_validRequest_returns204() throws Exception {
        doNothing().when(teamMemberService).removeMember(eq(1L), eq(2L), anyString());

        mockMvc.perform(delete("/api/teams/1/members/2"))
                .andExpect(status().isNoContent());

        verify(teamMemberService).removeMember(1L, 2L, "user");
    }

    @Test
    void removeMember_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/teams/1/members/2"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void removeMember_selfRemoval_returns400() throws Exception {
        doThrow(new BadRequestException("Cannot remove yourself from a team"))
                .when(teamMemberService).removeMember(eq(1L), eq(2L), anyString());

        mockMvc.perform(delete("/api/teams/1/members/2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void removeMember_forbidden_returns403() throws Exception {
        doThrow(new ForbiddenException("Only APP_ADMIN or TEAM_ADMIN can manage team members"))
                .when(teamMemberService).removeMember(eq(1L), eq(2L), anyString());

        mockMvc.perform(delete("/api/teams/1/members/2"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void removeMember_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("User", 99L))
                .when(teamMemberService).removeMember(eq(1L), eq(99L), anyString());

        mockMvc.perform(delete("/api/teams/1/members/99"))
                .andExpect(status().isNotFound());
    }

    // --- PATCH /api/teams/{teamId}/members/{userId} ---

    @Test
    @WithMockUser
    void changeRole_validRequest_returns200() throws Exception {
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("TEAM_ADMIN");
        when(teamMemberService.changeRole(eq(1L), eq(2L), any(), anyString()))
                .thenReturn(buildDTO());

        mockMvc.perform(patch("/api/teams/1/members/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(2));
    }

    @Test
    void changeRole_unauthenticated_returns401() throws Exception {
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("TEAM_ADMIN");

        mockMvc.perform(patch("/api/teams/1/members/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void changeRole_missingRole_returns400() throws Exception {
        ChangeRoleRequest req = new ChangeRoleRequest();

        mockMvc.perform(patch("/api/teams/1/members/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(teamMemberService, never()).changeRole(any(), any(), any(), any());
    }

    @Test
    @WithMockUser
    void changeRole_memberForbidden_returns403() throws Exception {
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("MEMBER");
        when(teamMemberService.changeRole(eq(1L), eq(2L), any(), anyString()))
                .thenThrow(new ForbiddenException("Only APP_ADMIN or TEAM_ADMIN can manage team members"));

        mockMvc.perform(patch("/api/teams/1/members/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void changeRole_selfChange_returns400() throws Exception {
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("MEMBER");
        when(teamMemberService.changeRole(eq(1L), eq(2L), any(), anyString()))
                .thenThrow(new BadRequestException("Cannot change your own role"));

        mockMvc.perform(patch("/api/teams/1/members/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
