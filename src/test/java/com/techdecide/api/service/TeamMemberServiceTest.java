package com.techdecide.api.service;

import com.techdecide.api.dto.team.AssignMemberRequest;
import com.techdecide.api.dto.team.ChangeRoleRequest;
import com.techdecide.api.dto.team.TeamMemberDTO;
import com.techdecide.api.entity.Team;
import com.techdecide.api.entity.User;
import com.techdecide.api.exception.BadRequestException;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.TeamRepository;
import com.techdecide.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamMemberServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TeamRepository teamRepository;
    @InjectMocks private TeamMemberService teamMemberService;

    private Team buildTeam() {
        return Team.builder().id(1L).name("Engineering").build();
    }

    private User buildAdmin() {
        User u = new User();
        u.setId(1L);
        u.setName("Admin User");
        u.setEmail("admin@example.com");
        u.setRole(User.Role.ADMIN);
        u.setTeam(buildTeam());
        return u;
    }

    private User buildTechLead() {
        User u = new User();
        u.setId(3L);
        u.setName("Tech Lead");
        u.setEmail("lead@example.com");
        u.setRole(User.Role.TECH_LEAD);
        u.setTeam(buildTeam());
        return u;
    }

    private User buildMember() {
        User u = new User();
        u.setId(2L);
        u.setName("Alice");
        u.setEmail("alice@example.com");
        u.setRole(User.Role.MEMBER);
        u.setTeam(buildTeam());
        return u;
    }

    // --- getMembers ---

    @Test
    void getMembers_asAdmin_returnsList() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(buildTeam()));
        when(userRepository.findAllByTeamId(1L)).thenReturn(List.of(buildMember()));

        List<TeamMemberDTO> result = teamMemberService.getMembers(1L, "admin@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Alice");
        assertThat(result.get(0).getRole()).isEqualTo("MEMBER");
    }

    @Test
    void getMembers_asTechLead_returnsList() {
        when(userRepository.findByEmail("lead@example.com")).thenReturn(Optional.of(buildTechLead()));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(buildTeam()));
        when(userRepository.findAllByTeamId(1L)).thenReturn(List.of(buildMember()));

        List<TeamMemberDTO> result = teamMemberService.getMembers(1L, "lead@example.com");

        assertThat(result).hasSize(1);
    }

    @Test
    void getMembers_asMember_throwsForbidden() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildMember()));

        assertThrows(ForbiddenException.class,
                () -> teamMemberService.getMembers(1L, "alice@example.com"));
    }

    @Test
    void getMembers_teamNotFound_throws404() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> teamMemberService.getMembers(99L, "admin@example.com"));
    }

    // --- assignMember ---

    @Test
    void assignMember_asAdmin_assignsAndReturnsDTO() {
        User target = buildMember();
        target.setTeam(null);
        AssignMemberRequest req = new AssignMemberRequest();
        req.setUserId(2L);

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(buildTeam()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TeamMemberDTO result = teamMemberService.assignMember(1L, req, "admin@example.com");

        assertThat(result.getUserId()).isEqualTo(2L);
        assertThat(result.getTeamId()).isEqualTo(1L);
    }

    @Test
    void assignMember_asMember_throwsForbidden() {
        AssignMemberRequest req = new AssignMemberRequest();
        req.setUserId(2L);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildMember()));

        assertThrows(ForbiddenException.class,
                () -> teamMemberService.assignMember(1L, req, "alice@example.com"));
    }

    @Test
    void assignMember_targetUserNotFound_throws404() {
        AssignMemberRequest req = new AssignMemberRequest();
        req.setUserId(99L);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(buildTeam()));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> teamMemberService.assignMember(1L, req, "admin@example.com"));
    }

    // --- removeMember ---

    @Test
    void removeMember_asAdmin_removes() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(buildTeam()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(buildMember()));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        teamMemberService.removeMember(1L, 2L, "admin@example.com");

        verify(userRepository).save(any());
    }

    @Test
    void removeMember_selfRemoval_throwsBadRequest() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));

        assertThrows(BadRequestException.class,
                () -> teamMemberService.removeMember(1L, 1L, "admin@example.com"));
    }

    @Test
    void removeMember_asMember_throwsForbidden() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildMember()));

        assertThrows(ForbiddenException.class,
                () -> teamMemberService.removeMember(1L, 99L, "alice@example.com"));
    }

    @Test
    void removeMember_userNotInTeam_throwsBadRequest() {
        User target = buildMember();
        target.setTeam(null);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(buildTeam()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThrows(BadRequestException.class,
                () -> teamMemberService.removeMember(1L, 2L, "admin@example.com"));
    }

    // --- changeRole ---

    @Test
    void changeRole_asAdmin_changesRole() {
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("TECH_LEAD");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(buildTeam()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(buildMember()));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TeamMemberDTO result = teamMemberService.changeRole(1L, 2L, req, "admin@example.com");

        assertThat(result.getRole()).isEqualTo("TECH_LEAD");
    }

    @Test
    void changeRole_asTechLead_throwsForbidden() {
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("MEMBER");
        when(userRepository.findByEmail("lead@example.com")).thenReturn(Optional.of(buildTechLead()));

        assertThrows(ForbiddenException.class,
                () -> teamMemberService.changeRole(1L, 2L, req, "lead@example.com"));
    }

    @Test
    void changeRole_selfChange_throwsBadRequest() {
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("MEMBER");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));

        assertThrows(BadRequestException.class,
                () -> teamMemberService.changeRole(1L, 1L, req, "admin@example.com"));
    }

    @Test
    void changeRole_invalidRole_throwsBadRequest() {
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("SUPERUSER");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(buildTeam()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(buildMember()));

        assertThrows(BadRequestException.class,
                () -> teamMemberService.changeRole(1L, 2L, req, "admin@example.com"));
    }

    @Test
    void changeRole_userNotInTeam_throwsBadRequest() {
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("TECH_LEAD");
        User target = buildMember();
        target.setTeam(null);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAdmin()));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(buildTeam()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThrows(BadRequestException.class,
                () -> teamMemberService.changeRole(1L, 2L, req, "admin@example.com"));
    }
}
