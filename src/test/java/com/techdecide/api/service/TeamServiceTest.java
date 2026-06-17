package com.techdecide.api.service;

import com.techdecide.api.dto.team.CreateTeamRequest;
import com.techdecide.api.dto.team.TeamDTO;
import com.techdecide.api.dto.team.UpdateTeamRequest;
import com.techdecide.api.entity.Organization;
import com.techdecide.api.entity.Team;
import com.techdecide.api.entity.TeamMembership;
import com.techdecide.api.entity.User;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.OrganizationRepository;
import com.techdecide.api.repository.TeamMembershipRepository;
import com.techdecide.api.repository.TeamRepository;
import com.techdecide.api.repository.UserRepository;
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
class TeamServiceTest {
    // QA matrix coverage:
    // TEAM-01 TEAM-02 TEAM-03 TEAM-04 TEAM-06

    @Mock private TeamRepository teamRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @Mock private TeamMembershipRepository teamMembershipRepository;
    @InjectMocks private TeamService teamService;

    private Organization buildOrg() {
        return Organization.builder().id(1L).name("Acme").build();
    }

    private Team buildTeam() {
        return Team.builder().id(1L).name("Engineering")
                .organization(buildOrg()).createdAt(LocalDateTime.now()).build();
    }

    private User buildAppAdmin() {
        return User.builder().id(99L).name("Admin").email("admin@example.com")
                .password("pw").appRole("APP_ADMIN").build();
    }

    private User buildMember() {
        return User.builder().id(1L).name("Alice").email("alice@example.com")
                .password("pw").appRole("USER").build();
    }

    private CreateTeamRequest buildRequest() {
        CreateTeamRequest req = new CreateTeamRequest();
        req.setName("Engineering");
        req.setOrganizationId(1L);
        return req;
    }

    // --- create ---

    @Test
    void create_validRequest_returnsTeamDTO() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(buildOrg()));
        when(teamRepository.existsByNameAndOrganizationId("Engineering", 1L)).thenReturn(false);
        when(teamRepository.save(any())).thenReturn(buildTeam());

        TeamDTO result = teamService.create(buildRequest());

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Engineering");
        assertThat(result.getOrganizationId()).isEqualTo(1L);
        assertThat(result.getOrganizationName()).isEqualTo("Acme");
    }

    @Test
    void create_organizationNotFound_throwsResourceNotFoundException() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.create(buildRequest()));
        verify(teamRepository, never()).save(any());
    }

    @Test
    void create_duplicateNameInOrg_throwsConflictException() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(buildOrg()));
        when(teamRepository.existsByNameAndOrganizationId("Engineering", 1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> teamService.create(buildRequest()));
        verify(teamRepository, never()).save(any());
    }

    @Test
    void create_sameNameDifferentOrg_doesNotConflict() {
        Organization org2 = Organization.builder().id(2L).name("Beta").build();
        CreateTeamRequest req = buildRequest();
        req.setOrganizationId(2L);
        when(organizationRepository.findById(2L)).thenReturn(Optional.of(org2));
        when(teamRepository.existsByNameAndOrganizationId("Engineering", 2L)).thenReturn(false);
        Team team2 = Team.builder().id(2L).name("Engineering")
                .organization(org2).createdAt(LocalDateTime.now()).build();
        when(teamRepository.save(any())).thenReturn(team2);

        TeamDTO result = teamService.create(req);

        assertThat(result).isNotNull();
    }

    // --- getAll ---

    @Test
    void getAll_appAdmin_returnsAllTeams() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAppAdmin()));
        when(teamRepository.findAll()).thenReturn(List.of(buildTeam()));

        List<TeamDTO> result = teamService.getAll("admin@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Engineering");
    }

    @Test
    void getAll_member_returnsOnlyOwnTeam() {
        User alice = buildMember();
        Team team = buildTeam();
        TeamMembership membership = TeamMembership.builder()
                .id(1L).user(alice).team(team).teamRole("MEMBER").build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(teamMembershipRepository.findByUserId(1L)).thenReturn(Optional.of(membership));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));

        List<TeamDTO> result = teamService.getAll("alice@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        verify(teamRepository, never()).findAll();
    }

    @Test
    void getAll_noTeam_returnsEmptyList() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildMember()));
        when(teamMembershipRepository.findByUserId(1L)).thenReturn(Optional.empty());

        List<TeamDTO> result = teamService.getAll("alice@example.com");

        assertThat(result).isEmpty();
        verify(teamRepository, never()).findAll();
    }

    // --- getByOrganization ---

    @Test
    void getByOrganization_existingOrg_returnsMappedList() {
        when(teamRepository.findByOrganizationId(1L)).thenReturn(List.of(buildTeam()));

        List<TeamDTO> result = teamService.getByOrganization(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrganizationId()).isEqualTo(1L);
    }

    @Test
    void getByOrganization_noTeams_returnsEmptyList() {
        when(teamRepository.findByOrganizationId(99L)).thenReturn(List.of());

        assertThat(teamService.getByOrganization(99L)).isEmpty();
    }

    // --- getById ---

    @Test
    void getById_existingId_returnsTeamDTO() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(buildTeam()));

        TeamDTO result = teamService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getById_nonExistingId_throwsResourceNotFoundException() {
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.getById(99L));
    }

    // --- update ---

    @Test
    void update_validRequest_updatesAndReturnsDTO() {
        Team existing = buildTeam();
        UpdateTeamRequest req = new UpdateTeamRequest();
        req.setName("Backend");
        req.setOrganizationId(1L);
        when(teamRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(buildOrg()));
        when(teamRepository.existsByNameAndOrganizationId("Backend", 1L)).thenReturn(false);
        when(teamRepository.save(any())).thenReturn(existing);

        TeamDTO result = teamService.update(1L, req);

        assertThat(result).isNotNull();
        verify(teamRepository).save(existing);
    }

    @Test
    void update_nonExistingId_throwsResourceNotFoundException() {
        UpdateTeamRequest req = new UpdateTeamRequest();
        req.setName("Engineering");
        req.setOrganizationId(1L);
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> teamService.update(99L, req));
        verify(teamRepository, never()).save(any());
    }

    @Test
    void update_organizationNotFound_throwsResourceNotFoundException() {
        UpdateTeamRequest req = new UpdateTeamRequest();
        req.setName("Engineering");
        req.setOrganizationId(1L);
        when(teamRepository.findById(1L)).thenReturn(Optional.of(buildTeam()));
        when(organizationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> teamService.update(1L, req));
    }

    @Test
    void update_duplicateNameInOrg_throwsConflictException() {
        Team existing = buildTeam();
        existing.setName("OldName");
        UpdateTeamRequest req = new UpdateTeamRequest();
        req.setName("Engineering");
        req.setOrganizationId(1L);
        when(teamRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(buildOrg()));
        when(teamRepository.existsByNameAndOrganizationId("Engineering", 1L)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> teamService.update(1L, req));
    }

    // --- delete ---

    @Test
    void delete_existingId_callsRepositoryDelete() {
        Team existing = buildTeam();
        when(teamRepository.findById(1L)).thenReturn(Optional.of(existing));

        teamService.delete(1L);

        verify(teamRepository).delete(existing);
    }

    @Test
    void delete_nonExistingId_throwsResourceNotFoundException() {
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.delete(99L));
        verify(teamRepository, never()).delete(any());
    }
}
