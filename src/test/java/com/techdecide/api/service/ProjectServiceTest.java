package com.techdecide.api.service;

import com.techdecide.api.dto.project.CreateProjectRequest;
import com.techdecide.api.dto.project.ProjectDTO;
import com.techdecide.api.dto.project.ProjectTeamDTO;
import com.techdecide.api.entity.*;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    // QA matrix coverage:
    // PROJ-01 PROJ-02 PROJ-05 PROJ-06 PROJ-07 PROJ-08
    // PROJ-09 PROJ-13 PROJ-14 SEC-02

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectTeamRepository projectTeamRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamMembershipRepository teamMembershipRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ProjectService projectService;

    // ── Builders ─────────────────────────────────────────────────────────────────

    private User buildAdmin() {
        return User.builder().id(1L).name("Admin").email("admin@test.com")
                .password("pw").appRole("APP_ADMIN").build();
    }

    private User buildMember(Long id, String email) {
        return User.builder().id(id).name("User").email(email)
                .password("pw").appRole("USER").build();
    }

    private Organization buildOrg() {
        return Organization.builder().id(10L).name("Acme Corp").build();
    }

    private Team buildTeam(Long id, String name) {
        return Team.builder().id(id).name(name).organization(buildOrg()).build();
    }

    private Project buildProject() {
        return Project.builder()
                .id(100L).name("GTN").description("GTN project")
                .organization(buildOrg())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private TeamMembership buildMembership(User user, Team team, String role) {
        return TeamMembership.builder()
                .id(1L).user(user).team(team).teamRole(role)
                .createdAt(LocalDateTime.now()).build();
    }

    private CreateProjectRequest buildCreateRequest() {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setName("GTN");
        req.setDescription("GTN project");
        req.setOrganizationId(10L);
        return req;
    }

    // ── create ───────────────────────────────────────────────────────────────────

    @Test
    void create_appAdmin_returnsProjectDTO() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(buildAdmin()));
        when(organizationRepository.findById(10L)).thenReturn(Optional.of(buildOrg()));
        when(projectRepository.saveAndFlush(any())).thenReturn(buildProject());

        ProjectDTO result = projectService.create(buildCreateRequest(), "admin@test.com");

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getName()).isEqualTo("GTN");
        assertThat(result.getOrganizationId()).isEqualTo(10L);
        assertThat(result.getOrganizationName()).isEqualTo("Acme Corp");
    }

    @Test
    void create_member_throwsForbiddenException() {
        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(buildMember(2L, "member@test.com")));

        assertThrows(ForbiddenException.class,
                () -> projectService.create(buildCreateRequest(), "member@test.com"));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_teamAdmin_throwsForbiddenException() {
        User teamAdmin = buildMember(3L, "teamadmin@test.com");
        when(userRepository.findByEmail("teamadmin@test.com")).thenReturn(Optional.of(teamAdmin));

        assertThrows(ForbiddenException.class,
                () -> projectService.create(buildCreateRequest(), "teamadmin@test.com"));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    // ── delete ───────────────────────────────────────────────────────────────────

    @Test
    void delete_appAdmin_deletesProject() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(buildAdmin()));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(buildProject()));

        projectService.delete(100L, "admin@test.com");

        verify(projectTeamRepository).deleteAllByProjectId(100L);
        verify(projectRepository).deleteById(100L);
    }

    @Test
    void delete_member_throwsForbiddenException() {
        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(buildMember(2L, "member@test.com")));

        assertThrows(ForbiddenException.class,
                () -> projectService.delete(100L, "member@test.com"));
        verify(projectRepository, never()).delete(any());
    }

    // ── getAll ───────────────────────────────────────────────────────────────────

    @Test
    void getAll_appAdmin_seesAllProjects() {
        Project p2 = Project.builder().id(101L).name("P2").organization(buildOrg())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(buildAdmin()));
        when(projectRepository.findAll()).thenReturn(List.of(buildProject(), p2));
        when(projectTeamRepository.findByProjectId(any())).thenReturn(List.of());

        List<ProjectDTO> result = projectService.getAll("admin@test.com");

        assertThat(result).hasSize(2);
    }

    @Test
    void getAll_memberWithTeam_seesOnlyAssignedProjects() {
        User member = buildMember(2L, "member@test.com");
        Team team = buildTeam(1L, "Backend Team");
        TeamMembership membership = buildMembership(member, team, "MEMBER");
        Project project = buildProject();
        ProjectTeam pt = ProjectTeam.builder().id(1L).project(project).team(team).build();

        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(member));
        when(teamMembershipRepository.findByUserId(2L)).thenReturn(Optional.of(membership));
        when(projectTeamRepository.findByTeamId(1L)).thenReturn(List.of(pt));
        when(projectTeamRepository.findByProjectId(100L)).thenReturn(List.of(pt));

        List<ProjectDTO> result = projectService.getAll("member@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(100L);
    }

    @Test
    void getAll_memberWithNoTeam_returnsEmpty() {
        User member = buildMember(2L, "member@test.com");
        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(member));
        when(teamMembershipRepository.findByUserId(2L)).thenReturn(Optional.empty());

        List<ProjectDTO> result = projectService.getAll("member@test.com");

        assertThat(result).isEmpty();
    }

    // ── assignTeam ───────────────────────────────────────────────────────────────

    @Test
    void assignTeam_appAdmin_createsProjectTeam() {
        Team team = buildTeam(1L, "Backend Team");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(buildAdmin()));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(buildProject()));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(projectTeamRepository.existsByProjectIdAndTeamId(100L, 1L)).thenReturn(false);
        when(projectTeamRepository.save(any())).thenReturn(
                ProjectTeam.builder().id(1L).project(buildProject()).team(team).build());

        ProjectTeamDTO result = projectService.assignTeam(100L, 1L, "admin@test.com");

        assertThat(result.getTeamId()).isEqualTo(1L);
        assertThat(result.getTeamName()).isEqualTo("Backend Team");
        verify(projectTeamRepository).save(any(ProjectTeam.class));
    }

    @Test
    void assignTeam_duplicateAssignment_throwsConflictException() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(buildAdmin()));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(buildProject()));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(buildTeam(1L, "Backend Team")));
        when(projectTeamRepository.existsByProjectIdAndTeamId(100L, 1L)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> projectService.assignTeam(100L, 1L, "admin@test.com"));
        verify(projectTeamRepository, never()).save(any());
    }

    @Test
    void assignTeam_member_throwsForbiddenException() {
        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(buildMember(2L, "member@test.com")));

        assertThrows(ForbiddenException.class,
                () -> projectService.assignTeam(100L, 1L, "member@test.com"));
    }

    // ── removeTeam ───────────────────────────────────────────────────────────────

    @Test
    void removeTeam_appAdmin_deletesProjectTeam() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(buildAdmin()));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(buildProject()));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(buildTeam(1L, "Backend Team")));
        when(projectTeamRepository.existsByProjectIdAndTeamId(100L, 1L)).thenReturn(true);

        projectService.removeTeam(100L, 1L, "admin@test.com");

        verify(projectTeamRepository).deleteByProjectIdAndTeamId(100L, 1L);
    }

    @Test
    void removeTeam_member_throwsForbiddenException() {
        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(buildMember(2L, "member@test.com")));

        assertThrows(ForbiddenException.class,
                () -> projectService.removeTeam(100L, 1L, "member@test.com"));
        verify(projectTeamRepository, never()).deleteByProjectIdAndTeamId(any(), any());
    }

    // ── getAvailableTeams ────────────────────────────────────────────────────────

    @Test
    void getAvailableTeams_returnsTeamsNotYetAssigned() {
        Team team1 = buildTeam(1L, "Backend Team");
        Team team2 = buildTeam(2L, "Devops Team");
        ProjectTeam assigned = ProjectTeam.builder().id(1L).project(buildProject()).team(team1).build();

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(buildAdmin()));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(buildProject()));
        when(teamRepository.findByOrganizationId(10L)).thenReturn(List.of(team1, team2));
        when(projectTeamRepository.findByProjectId(100L)).thenReturn(List.of(assigned));

        List<ProjectTeamDTO> result = projectService.getAvailableTeams(100L, "admin@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTeamName()).isEqualTo("Devops Team");
    }

    @Test
    void getAvailableTeams_member_throwsForbiddenException() {
        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(buildMember(2L, "member@test.com")));

        assertThrows(ForbiddenException.class,
                () -> projectService.getAvailableTeams(100L, "member@test.com"));
    }
}
