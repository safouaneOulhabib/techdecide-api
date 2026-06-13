package com.techdecide.api.service;

import com.techdecide.api.dto.team.AssignMemberRequest;
import com.techdecide.api.dto.team.AvailableUserDTO;
import com.techdecide.api.dto.team.ChangeRoleRequest;
import com.techdecide.api.dto.team.TeamMemberDTO;
import com.techdecide.api.entity.Team;
import com.techdecide.api.entity.TeamMembership;
import com.techdecide.api.entity.User;
import com.techdecide.api.exception.BadRequestException;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.TeamMembershipRepository;
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
    @Mock private TeamMembershipRepository teamMembershipRepository;
    @InjectMocks private TeamMemberService teamMemberService;

    private Team buildTeam() {
        return Team.builder().id(1L).name("Engineering").build();
    }

    private User buildAppAdmin() {
        User u = new User();
        u.setId(1L);
        u.setName("Admin User");
        u.setEmail("admin@example.com");
        u.setAppRole("APP_ADMIN");
        return u;
    }

    private User buildTeamAdmin() {
        User u = new User();
        u.setId(3L);
        u.setName("Team Lead");
        u.setEmail("lead@example.com");
        u.setAppRole("USER");
        return u;
    }

    private User buildMember() {
        User u = new User();
        u.setId(2L);
        u.setName("Alice");
        u.setEmail("alice@example.com");
        u.setAppRole("USER");
        return u;
    }

    private TeamMembership buildTeamAdminMembership(User user, Team team) {
        return TeamMembership.builder().id(10L).user(user).team(team).teamRole("TEAM_ADMIN").build();
    }

    private TeamMembership buildMemberMembership(User user, Team team) {
        return TeamMembership.builder().id(11L).user(user).team(team).teamRole("MEMBER").build();
    }

    // --- getMembers ---

    @Test
    void getMembers_asAppAdmin_returnsList() {
        User admin = buildAppAdmin();
        User member = buildMember();
        Team team = buildTeam();
        TeamMembership membership = buildMemberMembership(member, team);

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamMembershipRepository.findByTeamId(1L)).thenReturn(List.of(membership));

        List<TeamMemberDTO> result = teamMemberService.getMembers(1L, "admin@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Alice");
        assertThat(result.get(0).getTeamRole()).isEqualTo("MEMBER");
    }

    @Test
    void getMembers_asTeamAdmin_returnsList() {
        User lead = buildTeamAdmin();
        User member = buildMember();
        Team team = buildTeam();
        TeamMembership leadMembership = buildTeamAdminMembership(lead, team);
        TeamMembership memberMembership = buildMemberMembership(member, team);

        when(userRepository.findByEmail("lead@example.com")).thenReturn(Optional.of(lead));
        when(teamMembershipRepository.findByUserIdAndTeamId(3L, 1L)).thenReturn(Optional.of(leadMembership));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamMembershipRepository.findByTeamId(1L)).thenReturn(List.of(memberMembership));

        List<TeamMemberDTO> result = teamMemberService.getMembers(1L, "lead@example.com");

        assertThat(result).hasSize(1);
    }

    @Test
    void getMembers_asMemberOfTeam_returnsList() {
        User member = buildMember();
        Team team = buildTeam();
        TeamMembership membership = buildMemberMembership(member, team);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(member));
        when(teamMembershipRepository.findByUserIdAndTeamId(2L, 1L)).thenReturn(Optional.of(membership));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamMembershipRepository.findByTeamId(1L)).thenReturn(List.of(membership));

        List<TeamMemberDTO> result = teamMemberService.getMembers(1L, "alice@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Alice");
        assertThat(result.get(0).getTeamRole()).isEqualTo("MEMBER");
    }

    @Test
    void getMembers_asMember_notInTeam_throwsForbidden() {
        User member = buildMember();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(member));
        when(teamMembershipRepository.findByUserIdAndTeamId(2L, 1L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> teamMemberService.getMembers(1L, "alice@example.com"));
    }

    @Test
    void getMembers_teamNotFound_throws404() {
        User admin = buildAppAdmin();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> teamMemberService.getMembers(99L, "admin@example.com"));
    }

    // --- assignMember ---

    @Test
    void assignMember_asAppAdmin_createsAndReturnsDTO() {
        User admin = buildAppAdmin();
        User target = buildMember();
        target.setId(2L);
        Team team = buildTeam();
        AssignMemberRequest req = new AssignMemberRequest();
        req.setUserId(2L);

        TeamMembership saved = buildMemberMembership(target, team);

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(teamMembershipRepository.existsByUserIdAndTeamId(2L, 1L)).thenReturn(false);
        when(teamMembershipRepository.save(any())).thenReturn(saved);

        TeamMemberDTO result = teamMemberService.assignMember(1L, req, "admin@example.com");

        assertThat(result.getUserId()).isEqualTo(2L);
        assertThat(result.getTeamId()).isEqualTo(1L);
        assertThat(result.getTeamRole()).isEqualTo("MEMBER");
    }

    @Test
    void assignMember_asMember_throwsForbidden() {
        User member = buildMember();
        AssignMemberRequest req = new AssignMemberRequest();
        req.setUserId(5L);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(member));
        when(teamMembershipRepository.findByUserIdAndTeamId(2L, 1L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> teamMemberService.assignMember(1L, req, "alice@example.com"));
    }

    @Test
    void assignMember_targetUserNotFound_throws404() {
        User admin = buildAppAdmin();
        Team team = buildTeam();
        AssignMemberRequest req = new AssignMemberRequest();
        req.setUserId(99L);

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> teamMemberService.assignMember(1L, req, "admin@example.com"));
    }

    @Test
    void assignMember_targetIsAppAdmin_throwsBadRequest() {
        User admin = buildAppAdmin();
        User targetAdmin = buildAppAdmin();
        targetAdmin.setId(99L);
        Team team = buildTeam();
        AssignMemberRequest req = new AssignMemberRequest();
        req.setUserId(99L);

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(userRepository.findById(99L)).thenReturn(Optional.of(targetAdmin));

        assertThrows(BadRequestException.class,
                () -> teamMemberService.assignMember(1L, req, "admin@example.com"));
    }

    @Test
    void assignMember_alreadyMember_throwsBadRequest() {
        User admin = buildAppAdmin();
        User target = buildMember();
        Team team = buildTeam();
        AssignMemberRequest req = new AssignMemberRequest();
        req.setUserId(2L);

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(teamMembershipRepository.existsByUserIdAndTeamId(2L, 1L)).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> teamMemberService.assignMember(1L, req, "admin@example.com"));
    }

    // --- removeMember ---

    @Test
    void removeMember_asAppAdmin_removes() {
        User admin = buildAppAdmin();
        User target = buildMember();
        Team team = buildTeam();
        TeamMembership membership = buildMemberMembership(target, team);

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamMembershipRepository.findByUserIdAndTeamId(2L, 1L)).thenReturn(Optional.of(membership));

        teamMemberService.removeMember(1L, 2L, "admin@example.com");

        verify(teamMembershipRepository).delete(membership);
    }

    @Test
    void removeMember_selfRemoval_throwsBadRequest() {
        User admin = buildAppAdmin();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        assertThrows(BadRequestException.class,
                () -> teamMemberService.removeMember(1L, 1L, "admin@example.com"));
    }

    @Test
    void removeMember_asMember_throwsForbidden() {
        User member = buildMember();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(member));
        when(teamMembershipRepository.findByUserIdAndTeamId(2L, 1L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> teamMemberService.removeMember(1L, 99L, "alice@example.com"));
    }

    @Test
    void removeMember_userNotInTeam_throwsBadRequest() {
        User admin = buildAppAdmin();
        Team team = buildTeam();

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamMembershipRepository.findByUserIdAndTeamId(2L, 1L)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> teamMemberService.removeMember(1L, 2L, "admin@example.com"));
    }

    // --- changeRole ---

    @Test
    void changeRole_asAppAdmin_changesTeamRole() {
        User admin = buildAppAdmin();
        User target = buildMember();
        Team team = buildTeam();
        TeamMembership membership = buildMemberMembership(target, team);
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("TEAM_ADMIN");

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamMembershipRepository.findByUserIdAndTeamId(2L, 1L)).thenReturn(Optional.of(membership));
        when(teamMembershipRepository.existsByTeamIdAndTeamRoleAndUserIdNot(1L, "TEAM_ADMIN", 2L)).thenReturn(false);
        when(teamMembershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TeamMemberDTO result = teamMemberService.changeRole(1L, 2L, req, "admin@example.com");

        assertThat(result.getTeamRole()).isEqualTo("TEAM_ADMIN");
    }

    @Test
    void changeRole_asTeamAdmin_changesTeamRole() {
        User lead = buildTeamAdmin();
        User target = buildMember();
        Team team = buildTeam();
        TeamMembership leadMembership = buildTeamAdminMembership(lead, team);
        TeamMembership targetMembership = buildMemberMembership(target, team);
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("TEAM_ADMIN");

        when(userRepository.findByEmail("lead@example.com")).thenReturn(Optional.of(lead));
        when(teamMembershipRepository.findByUserIdAndTeamId(3L, 1L)).thenReturn(Optional.of(leadMembership));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamMembershipRepository.findByUserIdAndTeamId(2L, 1L)).thenReturn(Optional.of(targetMembership));
        when(teamMembershipRepository.existsByTeamIdAndTeamRoleAndUserIdNot(1L, "TEAM_ADMIN", 2L)).thenReturn(false);
        when(teamMembershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TeamMemberDTO result = teamMemberService.changeRole(1L, 2L, req, "lead@example.com");

        assertThat(result.getTeamRole()).isEqualTo("TEAM_ADMIN");
    }

    @Test
    void changeRole_toTeamAdmin_whenTeamAlreadyHasAdmin_throwsBadRequest() {
        User admin = buildAppAdmin();
        User target = buildMember();
        Team team = buildTeam();
        TeamMembership membership = buildMemberMembership(target, team);
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("TEAM_ADMIN");

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamMembershipRepository.findByUserIdAndTeamId(2L, 1L)).thenReturn(Optional.of(membership));
        when(teamMembershipRepository.existsByTeamIdAndTeamRoleAndUserIdNot(1L, "TEAM_ADMIN", 2L)).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> teamMemberService.changeRole(1L, 2L, req, "admin@example.com"));
    }

    @Test
    void changeRole_asMember_throwsForbidden() {
        User member = buildMember();
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("TEAM_ADMIN");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(member));
        when(teamMembershipRepository.findByUserIdAndTeamId(2L, 1L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> teamMemberService.changeRole(1L, 99L, req, "alice@example.com"));
    }

    @Test
    void changeRole_selfChange_throwsBadRequest() {
        User admin = buildAppAdmin();
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("MEMBER");

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        assertThrows(BadRequestException.class,
                () -> teamMemberService.changeRole(1L, 1L, req, "admin@example.com"));
    }

    @Test
    void changeRole_invalidRole_throwsBadRequest() {
        User admin = buildAppAdmin();
        User target = buildMember();
        Team team = buildTeam();
        TeamMembership membership = buildMemberMembership(target, team);
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("SUPERUSER");

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamMembershipRepository.findByUserIdAndTeamId(2L, 1L)).thenReturn(Optional.of(membership));

        assertThrows(BadRequestException.class,
                () -> teamMemberService.changeRole(1L, 2L, req, "admin@example.com"));
    }

    @Test
    void changeRole_userNotInTeam_throwsBadRequest() {
        User admin = buildAppAdmin();
        Team team = buildTeam();
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole("TEAM_ADMIN");

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamMembershipRepository.findByUserIdAndTeamId(2L, 1L)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> teamMemberService.changeRole(1L, 2L, req, "admin@example.com"));
    }

    // --- getAvailableUsers ---

    @Test
    void getAvailableUsers_asAppAdmin_returnsUsersWithNoMembership() {
        User admin = buildAppAdmin();
        User unassigned = buildMember();
        Team team = buildTeam();

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(userRepository.findUsersWithNoTeamMembership()).thenReturn(List.of(unassigned));

        List<AvailableUserDTO> result = teamMemberService.getAvailableUsers(1L, "admin@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Alice");
    }

    @Test
    void getAvailableUsers_asMember_throwsForbidden() {
        User member = buildMember();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(member));
        when(teamMembershipRepository.findByUserIdAndTeamId(2L, 1L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> teamMemberService.getAvailableUsers(1L, "alice@example.com"));
    }

    // --- APP_ADMIN-specific rules ---

    @Test
    void teamAdminInTeamACannotManageMembersOfTeamB() {
        User lead = buildTeamAdmin();
        // lead has TEAM_ADMIN for team 1, but tries to access team 2
        when(userRepository.findByEmail("lead@example.com")).thenReturn(Optional.of(lead));
        when(teamMembershipRepository.findByUserIdAndTeamId(3L, 2L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> teamMemberService.getMembers(2L, "lead@example.com"));
    }

    @Test
    void appAdminCanManageMembersOfAnyTeam() {
        User admin = buildAppAdmin();
        Team teamB = Team.builder().id(2L).name("Design").build();

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(teamRepository.findById(2L)).thenReturn(Optional.of(teamB));
        when(teamMembershipRepository.findByTeamId(2L)).thenReturn(List.of());

        List<TeamMemberDTO> result = teamMemberService.getMembers(2L, "admin@example.com");

        assertThat(result).isEmpty();
    }
}
