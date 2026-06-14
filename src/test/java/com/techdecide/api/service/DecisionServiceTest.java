package com.techdecide.api.service;

import com.techdecide.api.dto.decision.CreateDecisionRequest;
import com.techdecide.api.dto.decision.DecisionDTO;
import com.techdecide.api.dto.decision.UpdateDecisionRequest;
import com.techdecide.api.entity.*;
import com.techdecide.api.exception.BadRequestException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionServiceTest {

    @Mock private DecisionRepository decisionRepository;
    @Mock private DecisionTeamRepository decisionTeamRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectTeamRepository projectTeamRepository;
    @Mock private UserRepository userRepository;
    @Mock private TagRepository tagRepository;
    @Mock private TeamMembershipRepository teamMembershipRepository;
    @InjectMocks private DecisionService decisionService;

    // ── fixture builders ─────────────────────────────────────────────────────

    private Organization buildOrg() {
        return Organization.builder().id(1L).name("Acme").build();
    }

    private Team buildTeam1() {
        return Team.builder().id(1L).name("Backend Team").organization(buildOrg()).build();
    }

    private Team buildTeam2() {
        return Team.builder().id(2L).name("Devops Team").organization(buildOrg()).build();
    }

    private Project buildProject() {
        return Project.builder().id(1L).name("GTN").description("GTN project")
                .organization(buildOrg()).build();
    }

    private User buildUser() {
        return User.builder().id(1L).name("Alice").email("alice@example.com")
                .password("pw").appRole("USER").build();
    }

    private User buildDevopsUser() {
        return User.builder().id(4L).name("DevopsLead").email("devops@example.com")
                .password("pw").appRole("USER").build();
    }

    private User buildAppAdmin() {
        return User.builder().id(99L).name("Admin").email("admin@example.com")
                .password("pw").appRole("APP_ADMIN").build();
    }

    private User buildNoTeamUser() {
        return User.builder().id(7L).name("Lone").email("lone@example.com")
                .password("pw").appRole("USER").build();
    }

    private TeamMembership buildMembership(User user, Team team, String role) {
        return TeamMembership.builder().id(10L).user(user).team(team).teamRole(role).build();
    }

    private Tag buildTag() {
        return Tag.builder().id(1L).name("backend").color("#ff0000").build();
    }

    private Decision buildDecision() {
        Decision d = Decision.builder()
                .id(1L).title("Use PostgreSQL").context("We need a DB")
                .decision("PostgreSQL is chosen").consequences("Cost implications")
                .status(Decision.Status.DRAFT)
                .author(buildUser()).project(buildProject())
                .tags(new ArrayList<>()).alternatives(new ArrayList<>())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        d.setDecisionTeams(new ArrayList<>());
        return d;
    }

    private DecisionTeam buildDecisionTeam(Decision decision, Team team) {
        return DecisionTeam.builder().id(10L).decision(decision).team(team).build();
    }

    private CreateDecisionRequest buildCreateRequest() {
        CreateDecisionRequest req = new CreateDecisionRequest();
        req.setTitle("Use PostgreSQL");
        req.setContext("We need a DB");
        req.setDecision("PostgreSQL is chosen");
        req.setConsequences("Cost implications");
        req.setProjectId(1L);
        req.setTeamIds(List.of(1L));
        return req;
    }

    // Shorthand: mock Alice in Team1 (MEMBER by default) with project membership
    private void mockAliceInTeam1(String role) {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildUser()));
        when(teamMembershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(buildMembership(buildUser(), buildTeam1(), role)));
    }

    private void mockAliceProjectMember() {
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
    }

    private void mockAliceOnInvolvedTeam() {
        when(decisionTeamRepository.existsByDecisionIdAndTeamId(eq(1L), eq(1L))).thenReturn(true);
    }

    private void mockDecisionTeamsForDecision1() {
        when(decisionTeamRepository.findByDecisionId(1L))
                .thenReturn(List.of(buildDecisionTeam(buildDecision(), buildTeam1())));
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_validRequest_memberIncludesOwnTeam_returns201() {
        CreateDecisionRequest req = buildCreateRequest();
        Decision saved = buildDecision();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildUser()));
        when(teamMembershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(buildMembership(buildUser(), buildTeam1(), "MEMBER")));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(buildProject()));
        when(decisionRepository.save(any())).thenReturn(saved);
        when(decisionTeamRepository.saveAll(any())).thenReturn(List.of());
        when(decisionTeamRepository.findByDecisionId(1L)).thenReturn(List.of(buildDecisionTeam(saved, buildTeam1())));

        DecisionDTO result = decisionService.create(req, "alice@example.com");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getProjectId()).isEqualTo(1L);
        assertThat(result.getProjectName()).isEqualTo("GTN");
    }

    @Test
    void create_noTeam_throwsForbiddenException() {
        CreateDecisionRequest req = buildCreateRequest();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildUser()));
        when(teamMembershipRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> decisionService.create(req, "alice@example.com"));
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void create_actorTeamNotInProject_throwsForbiddenException() {
        CreateDecisionRequest req = buildCreateRequest();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildUser()));
        when(teamMembershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(buildMembership(buildUser(), buildTeam1(), "MEMBER")));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> decisionService.create(req, "alice@example.com"));
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void create_requestedTeamNotInProject_throwsBadRequestException() {
        CreateDecisionRequest req = buildCreateRequest();
        req.setTeamIds(List.of(1L, 99L)); // 99 not in project
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildUser()));
        when(teamMembershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(buildMembership(buildUser(), buildTeam1(), "MEMBER")));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 99L)).thenReturn(false);

        assertThrows(BadRequestException.class, () -> decisionService.create(req, "alice@example.com"));
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void create_actorOwnTeamNotInTeamIds_throwsBadRequestException() {
        CreateDecisionRequest req = buildCreateRequest();
        req.setTeamIds(List.of(2L)); // actor is in team 1, not 2
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildUser()));
        when(teamMembershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(buildMembership(buildUser(), buildTeam1(), "MEMBER")));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 2L)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> decisionService.create(req, "alice@example.com"));
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void create_appAdmin_skipsActorTeamCheck() {
        CreateDecisionRequest req = buildCreateRequest();
        req.setTeamIds(List.of(2L)); // admin may create in any team subset
        Decision saved = buildDecision();

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAppAdmin()));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 2L)).thenReturn(true);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(buildProject()));
        when(decisionRepository.save(any())).thenReturn(saved);
        when(decisionTeamRepository.saveAll(any())).thenReturn(List.of());
        when(decisionTeamRepository.findByDecisionId(1L)).thenReturn(List.of(buildDecisionTeam(saved, buildTeam2())));

        DecisionDTO result = decisionService.create(req, "admin@example.com");
        assertThat(result.getId()).isEqualTo(1L);
        verify(teamMembershipRepository, never()).findByUserId(any());
    }

    @Test
    void create_projectNotFound_throwsResourceNotFoundException() {
        CreateDecisionRequest req = buildCreateRequest();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildUser()));
        when(teamMembershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(buildMembership(buildUser(), buildTeam1(), "MEMBER")));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> decisionService.create(req, "alice@example.com"));
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void create_withTagIds_fetchesTags() {
        CreateDecisionRequest req = buildCreateRequest();
        req.setTagIds(List.of(1L));
        Decision saved = buildDecision();
        saved.setTags(List.of(buildTag()));

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildUser()));
        when(teamMembershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(buildMembership(buildUser(), buildTeam1(), "MEMBER")));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(buildProject()));
        when(tagRepository.findAllById(List.of(1L))).thenReturn(List.of(buildTag()));
        when(decisionRepository.save(any())).thenReturn(saved);
        when(decisionTeamRepository.saveAll(any())).thenReturn(List.of());
        when(decisionTeamRepository.findByDecisionId(1L)).thenReturn(List.of());

        decisionService.create(req, "alice@example.com");
        verify(tagRepository).findAllById(List.of(1L));
    }

    // ── getAll ───────────────────────────────────────────────────────────────

    @Test
    void getAll_appAdmin_returnsAllDecisions() {
        Decision d1 = buildDecision();
        Decision d2 = buildDecision();
        d2.setId(2L);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAppAdmin()));
        when(decisionRepository.findAll()).thenReturn(List.of(d1, d2));
        when(decisionTeamRepository.findByDecisionId(anyLong())).thenReturn(List.of());

        List<DecisionDTO> result = decisionService.getAll("admin@example.com");
        assertThat(result).hasSize(2);
    }

    @Test
    void getAll_member_returnsOnlyProjectScopedDecisions() {
        Decision d1 = buildDecision();
        mockAliceInTeam1("MEMBER");
        when(decisionRepository.findVisibleToTeam(1L)).thenReturn(List.of(d1));
        when(decisionTeamRepository.findByDecisionId(1L)).thenReturn(List.of(buildDecisionTeam(d1, buildTeam1())));
        when(decisionTeamRepository.existsByDecisionIdAndTeamId(1L, 1L)).thenReturn(true);
        when(teamMembershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(buildMembership(buildUser(), buildTeam1(), "MEMBER")));

        List<DecisionDTO> result = decisionService.getAll("alice@example.com");
        assertThat(result).hasSize(1);
        verify(decisionRepository, never()).findAll();
    }

    @Test
    void getAll_noTeam_returnsEmptyList() {
        when(userRepository.findByEmail("lone@example.com")).thenReturn(Optional.of(buildNoTeamUser()));
        when(teamMembershipRepository.findByUserId(7L)).thenReturn(Optional.empty());

        List<DecisionDTO> result = decisionService.getAll("lone@example.com");
        assertThat(result).isEmpty();
        verify(decisionRepository, never()).findAll();
        verify(decisionRepository, never()).findVisibleToTeam(any());
    }

    // ── getById ──────────────────────────────────────────────────────────────

    @Test
    void getById_appAdmin_allowsAnyDecision() {
        Decision d = buildDecision();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAppAdmin()));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(d));
        when(decisionTeamRepository.findByDecisionId(1L)).thenReturn(List.of());

        DecisionDTO result = decisionService.getById(1L, "admin@example.com");
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getById_projectMemberNotOnInvolvedTeam_canViewReadOnly() {
        // Alice is in Team1 which is in project. D is Team2-only.
        // Alice still has VIEW access because she is a project member.
        Decision d = buildDecision();
        mockAliceInTeam1("MEMBER");
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(d));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
        when(decisionTeamRepository.findByDecisionId(1L)).thenReturn(List.of(buildDecisionTeam(d, buildTeam2())));
        when(decisionTeamRepository.existsByDecisionIdAndTeamId(1L, 1L)).thenReturn(false);

        DecisionDTO result = decisionService.getById(1L, "alice@example.com");
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.isCanVote()).isFalse();
    }

    @Test
    void getById_involvedTeamMember_canVote() {
        Decision d = buildDecision();
        mockAliceInTeam1("MEMBER");
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(d));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
        when(decisionTeamRepository.findByDecisionId(1L)).thenReturn(List.of(buildDecisionTeam(d, buildTeam1())));
        when(decisionTeamRepository.existsByDecisionIdAndTeamId(1L, 1L)).thenReturn(true);
        when(teamMembershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(buildMembership(buildUser(), buildTeam1(), "MEMBER")));

        DecisionDTO result = decisionService.getById(1L, "alice@example.com");
        assertThat(result.isCanVote()).isTrue();
        assertThat(result.isCanGovern()).isFalse();
    }

    @Test
    void getById_teamAdminOfInvolvedTeam_canGovern() {
        Decision d = buildDecision();
        mockAliceInTeam1("TEAM_ADMIN");
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(d));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
        when(decisionTeamRepository.findByDecisionId(1L)).thenReturn(List.of(buildDecisionTeam(d, buildTeam1())));
        when(decisionTeamRepository.existsByDecisionIdAndTeamId(1L, 1L)).thenReturn(true);
        when(teamMembershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(buildMembership(buildUser(), buildTeam1(), "TEAM_ADMIN")));

        DecisionDTO result = decisionService.getById(1L, "alice@example.com");
        assertThat(result.isCanGovern()).isTrue();
    }

    @Test
    void getById_userNotInProject_throwsForbiddenException() {
        Decision d = buildDecision();
        mockAliceInTeam1("MEMBER");
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(d));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> decisionService.getById(1L, "alice@example.com"));
    }

    @Test
    void getById_noTeam_throwsForbiddenException() {
        Decision d = buildDecision();
        when(userRepository.findByEmail("lone@example.com")).thenReturn(Optional.of(buildNoTeamUser()));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(d));
        when(teamMembershipRepository.findByUserId(7L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> decisionService.getById(1L, "lone@example.com"));
    }

    @Test
    void getById_nonExistingId_throwsResourceNotFoundException() {
        when(decisionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> decisionService.getById(99L, "alice@example.com"));
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_validRequest_involvedTeamMember_updatesAndReturnsDTO() {
        UpdateDecisionRequest req = new UpdateDecisionRequest();
        req.setTitle("Updated Title");

        Decision existing = buildDecision();
        Decision updated = buildDecision();
        updated.setTitle("Updated Title");

        mockAliceInTeam1("MEMBER");
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionTeamRepository.existsByDecisionIdAndTeamId(anyLong(), anyLong())).thenReturn(true);
        when(decisionRepository.save(any())).thenReturn(updated);
        when(decisionTeamRepository.findByDecisionId(1L)).thenReturn(List.of(buildDecisionTeam(updated, buildTeam1())));

        DecisionDTO result = decisionService.update(1L, req, "alice@example.com");
        assertThat(result.getTitle()).isEqualTo("Updated Title");
    }

    @Test
    void update_notOnInvolvedTeam_throwsForbiddenException() {
        Decision existing = buildDecision(); // involved team is Team2
        mockAliceInTeam1("MEMBER"); // Alice is in Team1
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionTeamRepository.existsByDecisionIdAndTeamId(1L, 1L)).thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> decisionService.update(1L, new UpdateDecisionRequest(), "alice@example.com"));
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void update_approvedDecision_throwsBadRequestException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.APPROVED);
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class,
                () -> decisionService.update(1L, new UpdateDecisionRequest(), "alice@example.com"));
        verify(decisionRepository, never()).save(any());
    }

    // ── updateStatus ─────────────────────────────────────────────────────────

    @Test
    void updateStatus_memberOfInvolvedTeam_canProposeDraft() {
        Decision existing = buildDecision(); // DRAFT, Team1 involved
        Decision saved = buildDecision();
        saved.setStatus(Decision.Status.PROPOSED);

        mockAliceInTeam1("MEMBER");
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
        when(decisionTeamRepository.existsByDecisionIdAndTeamId(1L, 1L)).thenReturn(true);
        when(decisionRepository.save(any())).thenReturn(saved);
        when(decisionTeamRepository.findByDecisionId(1L)).thenReturn(List.of(buildDecisionTeam(saved, buildTeam1())));

        DecisionDTO result = decisionService.updateStatus(1L, Decision.Status.PROPOSED, null, "alice@example.com");
        assertThat(result.getStatus()).isEqualTo(Decision.Status.PROPOSED);
    }

    @Test
    void updateStatus_memberOfInvolvedTeam_cannotApprove_throwsForbiddenException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.PROPOSED);

        mockAliceInTeam1("MEMBER");
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
        when(decisionTeamRepository.existsByDecisionIdAndTeamId(1L, 1L)).thenReturn(true);

        assertThrows(ForbiddenException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.APPROVED, null, "alice@example.com"));
    }

    @Test
    void updateStatus_teamAdminOfInvolvedTeam_canApprove() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.PROPOSED);
        Decision saved = buildDecision();
        saved.setStatus(Decision.Status.APPROVED);

        mockAliceInTeam1("TEAM_ADMIN");
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
        when(decisionTeamRepository.existsByDecisionIdAndTeamId(1L, 1L)).thenReturn(true);
        when(decisionRepository.save(any())).thenReturn(saved);
        when(decisionTeamRepository.findByDecisionId(1L)).thenReturn(List.of(buildDecisionTeam(saved, buildTeam1())));

        DecisionDTO result = decisionService.updateStatus(1L, Decision.Status.APPROVED, null, "alice@example.com");
        assertThat(result.getStatus()).isEqualTo(Decision.Status.APPROVED);
    }

    @Test
    void updateStatus_teamAdminOfDifferentInvolvedTeam_canApprove() {
        // Decision involved Team1 and Team2; Devops Lead is TEAM_ADMIN of Team2
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.PROPOSED);
        Decision saved = buildDecision();
        saved.setStatus(Decision.Status.APPROVED);

        when(userRepository.findByEmail("devops@example.com")).thenReturn(Optional.of(buildDevopsUser()));
        when(teamMembershipRepository.findByUserId(4L))
                .thenReturn(Optional.of(buildMembership(buildDevopsUser(), buildTeam2(), "TEAM_ADMIN")));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 2L)).thenReturn(true);
        when(decisionTeamRepository.existsByDecisionIdAndTeamId(1L, 2L)).thenReturn(true);
        when(decisionRepository.save(any())).thenReturn(saved);
        when(decisionTeamRepository.findByDecisionId(1L)).thenReturn(List.of(buildDecisionTeam(saved, buildTeam1())));

        DecisionDTO result = decisionService.updateStatus(1L, Decision.Status.APPROVED, null, "devops@example.com");
        assertThat(result.getStatus()).isEqualTo(Decision.Status.APPROVED);
    }

    @Test
    void updateStatus_projectMemberNotOnInvolvedTeam_throwsForbiddenException() {
        Decision existing = buildDecision(); // Team2 involved only
        existing.setStatus(Decision.Status.PROPOSED);

        mockAliceInTeam1("TEAM_ADMIN"); // Alice in Team1, which is in project but NOT involved
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
        when(decisionTeamRepository.existsByDecisionIdAndTeamId(1L, 1L)).thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.APPROVED, null, "alice@example.com"));
    }

    @Test
    void updateStatus_userNotInProject_throwsForbiddenException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.PROPOSED);

        mockAliceInTeam1("TEAM_ADMIN");
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.APPROVED, null, "alice@example.com"));
    }

    @Test
    void updateStatus_noTeam_throwsForbiddenException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.PROPOSED);

        when(userRepository.findByEmail("lone@example.com")).thenReturn(Optional.of(buildNoTeamUser()));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(teamMembershipRepository.findByUserId(7L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.APPROVED, null, "lone@example.com"));
    }

    @Test
    void updateStatus_appAdmin_canApproveAnyDecision() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.PROPOSED);
        Decision saved = buildDecision();
        saved.setStatus(Decision.Status.APPROVED);

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAppAdmin()));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionRepository.save(any())).thenReturn(saved);
        when(decisionTeamRepository.findByDecisionId(1L)).thenReturn(List.of());

        DecisionDTO result = decisionService.updateStatus(1L, Decision.Status.APPROVED, null, "admin@example.com");
        assertThat(result.getStatus()).isEqualTo(Decision.Status.APPROVED);
    }

    @Test
    void updateStatus_illegalTransition_throwsConflictException() {
        Decision existing = buildDecision(); // DRAFT
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.APPROVED, null, "alice@example.com"));
    }

    @Test
    void updateStatus_supersededWithoutSupersededById_throwsBadRequestException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.APPROVED);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAppAdmin()));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.SUPERSEDED, null, "admin@example.com"));
    }

    @Test
    void updateStatus_supersededBySelfReferential_throwsBadRequestException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.APPROVED);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAppAdmin()));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.SUPERSEDED, 1L, "admin@example.com"));
    }

    @Test
    void updateStatus_supersededByApprovedDecision_succeeds() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.APPROVED);
        Decision superseding = buildDecision();
        superseding.setId(2L);
        superseding.setStatus(Decision.Status.APPROVED);
        Decision saved = buildDecision();
        saved.setStatus(Decision.Status.SUPERSEDED);
        saved.setSupersededBy(superseding);

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAppAdmin()));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionRepository.findById(2L)).thenReturn(Optional.of(superseding));
        when(decisionRepository.save(any())).thenReturn(saved);
        when(decisionTeamRepository.findByDecisionId(1L)).thenReturn(List.of());

        DecisionDTO result = decisionService.updateStatus(1L, Decision.Status.SUPERSEDED, 2L, "admin@example.com");
        assertThat(result.getStatus()).isEqualTo(Decision.Status.SUPERSEDED);
        assertThat(result.getSupersededById()).isEqualTo(2L);
    }

    @Test
    void updateStatus_supersededByDraftDecision_throwsBadRequestException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.APPROVED);
        Decision superseding = buildDecision();
        superseding.setId(2L);
        superseding.setStatus(Decision.Status.DRAFT);

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAppAdmin()));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionRepository.findById(2L)).thenReturn(Optional.of(superseding));

        assertThrows(BadRequestException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.SUPERSEDED, 2L, "admin@example.com"));
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_involvedTeamMember_deletesSuccessfully() {
        Decision existing = buildDecision();
        mockAliceInTeam1("MEMBER");
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionTeamRepository.existsByDecisionIdAndTeamId(1L, 1L)).thenReturn(true);

        decisionService.delete(1L, "alice@example.com");
        verify(decisionRepository).delete(existing);
    }

    @Test
    void delete_notOnInvolvedTeam_throwsForbiddenException() {
        Decision existing = buildDecision();
        mockAliceInTeam1("MEMBER");
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionTeamRepository.existsByDecisionIdAndTeamId(1L, 1L)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> decisionService.delete(1L, "alice@example.com"));
        verify(decisionRepository, never()).delete(any());
    }

    @Test
    void delete_approvedDecision_throwsBadRequestException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.APPROVED);
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class, () -> decisionService.delete(1L, "alice@example.com"));
        verify(decisionRepository, never()).delete(any());
    }

    @Test
    void delete_supersededDecision_throwsBadRequestException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.SUPERSEDED);
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class, () -> decisionService.delete(1L, "alice@example.com"));
        verify(decisionRepository, never()).delete(any());
    }

    @Test
    void delete_nonExistingId_throwsResourceNotFoundException() {
        when(decisionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> decisionService.delete(99L, "alice@example.com"));
        verify(decisionRepository, never()).delete(any());
    }

    @Test
    void delete_appAdmin_deletesAnyDecision() {
        Decision existing = buildDecision();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAppAdmin()));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        decisionService.delete(1L, "admin@example.com");
        verify(decisionRepository).delete(existing);
    }
}
