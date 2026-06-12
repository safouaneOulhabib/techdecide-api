package com.techdecide.api.service;

import com.techdecide.api.dto.decision.CreateDecisionRequest;
import com.techdecide.api.dto.decision.DecisionDTO;
import com.techdecide.api.dto.decision.UpdateDecisionRequest;
import com.techdecide.api.entity.*;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.BadRequestException;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.DecisionRepository;
import com.techdecide.api.repository.TagRepository;
import com.techdecide.api.repository.TeamMembershipRepository;
import com.techdecide.api.repository.TeamRepository;
import com.techdecide.api.repository.UserRepository;
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
    @Mock private TeamRepository teamRepository;
    @Mock private UserRepository userRepository;
    @Mock private TagRepository tagRepository;
    @Mock private TeamMembershipRepository teamMembershipRepository;
    @InjectMocks private DecisionService decisionService;

    private User buildUser() {
        return User.builder().id(1L).name("Alice").email("alice@example.com")
                .password("pw").appRole("USER").build();
    }

    private User buildAppAdmin() {
        return User.builder().id(99L).name("Admin").email("admin@example.com")
                .password("pw").appRole("APP_ADMIN").build();
    }

    private void mockGovernanceAsAppAdmin() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(buildAppAdmin()));
    }

    private Organization buildOrg() {
        return Organization.builder().id(1L).name("Acme").build();
    }

    private Team buildTeam() {
        return Team.builder().id(1L).name("Engineering").organization(buildOrg()).build();
    }

    private Tag buildTag() {
        return Tag.builder().id(1L).name("backend").color("#ff0000").build();
    }

    private Decision buildDecision() {
        return Decision.builder()
                .id(1L).title("Use PostgreSQL").context("We need a DB")
                .decision("PostgreSQL is chosen").consequences("Cost implications")
                .status(Decision.Status.DRAFT)
                .author(buildUser()).team(buildTeam())
                .tags(new ArrayList<>()).alternatives(new ArrayList<>())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private CreateDecisionRequest buildCreateRequest() {
        CreateDecisionRequest req = new CreateDecisionRequest();
        req.setTitle("Use PostgreSQL");
        req.setContext("We need a DB");
        req.setDecision("PostgreSQL is chosen");
        req.setConsequences("Cost implications");
        req.setTeamId(1L);
        return req;
    }

    // --- create ---

    @Test
    void create_validRequest_returnsDecisionDTO() {
        CreateDecisionRequest req = buildCreateRequest();
        Decision saved = buildDecision();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildUser()));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(buildTeam()));
        when(decisionRepository.save(any())).thenReturn(saved);

        DecisionDTO result = decisionService.create(req, "alice@example.com");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Use PostgreSQL");
        assertThat(result.getAuthorName()).isEqualTo("Alice");
        assertThat(result.getTeamName()).isEqualTo("Engineering");
        assertThat(result.getStatus()).isEqualTo(Decision.Status.DRAFT);
    }

    @Test
    void create_userNotFound_throwsResourceNotFoundException() {
        CreateDecisionRequest req = buildCreateRequest();
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> decisionService.create(req, "nobody@example.com"));
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void create_teamNotFound_throwsResourceNotFoundException() {
        CreateDecisionRequest req = buildCreateRequest();
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(buildUser()));
        when(teamRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> decisionService.create(req, "alice@example.com"));
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void create_withTagIds_fetchesTags() {
        CreateDecisionRequest req = buildCreateRequest();
        req.setTagIds(List.of(1L, 2L));
        Decision saved = buildDecision();
        saved.setTags(List.of(buildTag()));
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(buildUser()));
        when(teamRepository.findById(any())).thenReturn(Optional.of(buildTeam()));
        when(tagRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(buildTag()));
        when(decisionRepository.save(any())).thenReturn(saved);

        decisionService.create(req, "alice@example.com");

        verify(tagRepository).findAllById(List.of(1L, 2L));
    }

    @Test
    void create_withNullTagIds_usesEmptyList() {
        CreateDecisionRequest req = buildCreateRequest();
        req.setTagIds(null);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(buildUser()));
        when(teamRepository.findById(any())).thenReturn(Optional.of(buildTeam()));
        when(decisionRepository.save(any())).thenReturn(buildDecision());

        decisionService.create(req, "alice@example.com");

        verify(tagRepository, never()).findAllById(any());
    }

    @Test
    void create_withAlternatives_mapsAlternativesAndLinksDecision() {
        CreateDecisionRequest req = buildCreateRequest();
        CreateDecisionRequest.AlternativeRequest alt = new CreateDecisionRequest.AlternativeRequest();
        alt.setName("MySQL");
        alt.setRejectionReason("Less features");
        req.setAlternatives(List.of(alt));

        Decision saved = buildDecision();
        Alternative alternative = Alternative.builder().id(1L).name("MySQL")
                .rejectionReason("Less features").build();
        saved.setAlternatives(List.of(alternative));

        when(userRepository.findByEmail(any())).thenReturn(Optional.of(buildUser()));
        when(teamRepository.findById(any())).thenReturn(Optional.of(buildTeam()));
        when(decisionRepository.save(any())).thenReturn(saved);

        DecisionDTO result = decisionService.create(req, "alice@example.com");

        assertThat(result.getAlternatives()).hasSize(1);
        assertThat(result.getAlternatives().get(0).getName()).isEqualTo("MySQL");
    }

    @Test
    void create_withNullAlternatives_usesEmptyList() {
        CreateDecisionRequest req = buildCreateRequest();
        req.setAlternatives(null);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(buildUser()));
        when(teamRepository.findById(any())).thenReturn(Optional.of(buildTeam()));
        when(decisionRepository.save(any())).thenReturn(buildDecision());

        DecisionDTO result = decisionService.create(req, "alice@example.com");

        assertThat(result.getAlternatives()).isEmpty();
    }

    // --- getAll ---

    @Test
    void getAll_multipleDecisions_returnsAllMapped() {
        Decision d1 = buildDecision();
        Decision d2 = buildDecision();
        d2.setId(2L);
        d2.setTitle("Use Redis");
        when(decisionRepository.findAll()).thenReturn(List.of(d1, d2));

        List<DecisionDTO> result = decisionService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DecisionDTO::getTitle)
                .containsExactlyInAnyOrder("Use PostgreSQL", "Use Redis");
    }

    @Test
    void getAll_emptyRepository_returnsEmptyList() {
        when(decisionRepository.findAll()).thenReturn(List.of());

        List<DecisionDTO> result = decisionService.getAll();

        assertThat(result).isEmpty();
    }

    // --- getById ---

    @Test
    void getById_existingId_returnsDecisionDTO() {
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(buildDecision()));

        DecisionDTO result = decisionService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Use PostgreSQL");
    }

    @Test
    void getById_nonExistingId_throwsResourceNotFoundException() {
        when(decisionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> decisionService.getById(99L));
    }

    // --- getByTeam ---

    @Test
    void getByTeam_existingTeam_returnsMappedList() {
        when(decisionRepository.findByTeamId(1L)).thenReturn(List.of(buildDecision()));

        List<DecisionDTO> result = decisionService.getByTeam(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTeamName()).isEqualTo("Engineering");
    }

    @Test
    void getByTeam_noDecisions_returnsEmptyList() {
        when(decisionRepository.findByTeamId(99L)).thenReturn(List.of());

        List<DecisionDTO> result = decisionService.getByTeam(99L);

        assertThat(result).isEmpty();
    }

    // --- search ---

    @Test
    void search_matchingKeyword_returnsMappedResults() {
        when(decisionRepository.searchByKeyword("postgres")).thenReturn(List.of(buildDecision()));

        List<DecisionDTO> result = decisionService.search("postgres");

        assertThat(result).hasSize(1);
    }

    @Test
    void search_noMatches_returnsEmptyList() {
        when(decisionRepository.searchByKeyword("zzz")).thenReturn(List.of());

        List<DecisionDTO> result = decisionService.search("zzz");

        assertThat(result).isEmpty();
    }

    // --- update ---

    @Test
    void update_validRequest_updatesAndReturnsDTO() {
        UpdateDecisionRequest req = new UpdateDecisionRequest();
        req.setTitle("Updated Title");
        req.setContext("Updated Context");

        Decision existing = buildDecision();
        Decision updated = buildDecision();
        updated.setTitle("Updated Title");

        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionRepository.save(any())).thenReturn(updated);

        DecisionDTO result = decisionService.update(1L, req);

        assertThat(result.getTitle()).isEqualTo("Updated Title");
        verify(decisionRepository).save(existing);
    }

    @Test
    void update_nonExistingId_throwsResourceNotFoundException() {
        when(decisionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> decisionService.update(99L, new UpdateDecisionRequest()));
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void update_withTagIds_replacesTags() {
        UpdateDecisionRequest req = new UpdateDecisionRequest();
        req.setTagIds(List.of(1L));
        Decision existing = buildDecision();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tagRepository.findAllById(List.of(1L))).thenReturn(List.of(buildTag()));
        when(decisionRepository.save(any())).thenReturn(existing);

        decisionService.update(1L, req);

        verify(tagRepository).findAllById(List.of(1L));
    }

    @Test
    void update_withAlternatives_replacesAlternatives() {
        UpdateDecisionRequest req = new UpdateDecisionRequest();
        CreateDecisionRequest.AlternativeRequest alt = new CreateDecisionRequest.AlternativeRequest();
        alt.setName("Option B");
        req.setAlternatives(List.of(alt));

        Decision existing = buildDecision();
        existing.setAlternatives(new ArrayList<>());
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionRepository.save(any())).thenReturn(existing);

        decisionService.update(1L, req);

        assertThat(existing.getAlternatives()).hasSize(1);
        assertThat(existing.getAlternatives().get(0).getName()).isEqualTo("Option B");
    }

    // --- updateStatus ---

    @Test
    void updateStatus_validId_updatesStatus() {
        Decision existing = buildDecision(); // DRAFT
        Decision updated = buildDecision();
        updated.setStatus(Decision.Status.PROPOSED);
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionRepository.save(any())).thenReturn(updated);

        DecisionDTO result = decisionService.updateStatus(1L, Decision.Status.PROPOSED, null, "alice@example.com");

        assertThat(result.getStatus()).isEqualTo(Decision.Status.PROPOSED);
    }

    @Test
    void updateStatus_nonExistingId_throwsResourceNotFoundException() {
        when(decisionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> decisionService.updateStatus(99L, Decision.Status.PROPOSED, null, "alice@example.com"));
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
        mockGovernanceAsAppAdmin();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.SUPERSEDED, null, "admin@example.com"));
    }

    @Test
    void updateStatus_supersededWithSelfReferentialId_throwsBadRequestException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.APPROVED);
        mockGovernanceAsAppAdmin();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.SUPERSEDED, 1L, "admin@example.com"));
    }

    @Test
    void updateStatus_supersededWithNonExistentSupersededById_throwsResourceNotFoundException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.APPROVED);
        mockGovernanceAsAppAdmin();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.SUPERSEDED, 999L, "admin@example.com"));
    }

    @Test
    void updateStatus_supersededByDraftDecision_throwsBadRequestException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.APPROVED);
        Decision superseding = buildDecision();
        superseding.setId(2L);
        superseding.setStatus(Decision.Status.DRAFT);
        mockGovernanceAsAppAdmin();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionRepository.findById(2L)).thenReturn(Optional.of(superseding));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.SUPERSEDED, 2L, "admin@example.com"));
        assertThat(ex.getMessage()).contains("APPROVED").contains("DRAFT");
    }

    @Test
    void updateStatus_supersededByProposedDecision_throwsBadRequestException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.APPROVED);
        Decision superseding = buildDecision();
        superseding.setId(2L);
        superseding.setStatus(Decision.Status.PROPOSED);
        mockGovernanceAsAppAdmin();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionRepository.findById(2L)).thenReturn(Optional.of(superseding));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.SUPERSEDED, 2L, "admin@example.com"));
        assertThat(ex.getMessage()).contains("APPROVED").contains("PROPOSED");
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
        mockGovernanceAsAppAdmin();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionRepository.findById(2L)).thenReturn(Optional.of(superseding));
        when(decisionRepository.save(any())).thenReturn(saved);

        DecisionDTO result = decisionService.updateStatus(1L, Decision.Status.SUPERSEDED, 2L, "admin@example.com");
        assertThat(result.getStatus()).isEqualTo(Decision.Status.SUPERSEDED);
    }

    @Test
    void updateStatus_supersededByApprovedDecision_dtoPropagatesIdAndTitle() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.APPROVED);

        Decision superseding = buildDecision();
        superseding.setId(2L);
        superseding.setTitle("Use MySQL");
        superseding.setStatus(Decision.Status.APPROVED);

        Decision saved = buildDecision();
        saved.setStatus(Decision.Status.SUPERSEDED);
        saved.setSupersededBy(superseding);

        mockGovernanceAsAppAdmin();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionRepository.findById(2L)).thenReturn(Optional.of(superseding));
        when(decisionRepository.save(any())).thenReturn(saved);

        DecisionDTO result = decisionService.updateStatus(1L, Decision.Status.SUPERSEDED, 2L, "admin@example.com");

        assertThat(result.getSupersededById()).isEqualTo(2L);
        assertThat(result.getSupersededByTitle()).isEqualTo("Use MySQL");
    }

    @Test
    void updateStatus_transitionOutOfSuperseded_throwsConflictException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.SUPERSEDED);
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.DRAFT, null, "alice@example.com"));
    }

    @Test
    void updateStatus_memberCannotApprove_throwsForbiddenException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.PROPOSED);
        User member = buildUser();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(member));
        when(teamMembershipRepository.findByUserIdAndTeamId(member.getId(), existing.getTeam().getId()))
                .thenReturn(Optional.empty());

        assertThrows(com.techdecide.api.exception.ForbiddenException.class,
                () -> decisionService.updateStatus(1L, Decision.Status.APPROVED, null, "alice@example.com"));
    }

    @Test
    void updateStatus_teamAdminCanApproveInOwnTeam() {
        Team team = buildTeam();
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.PROPOSED);
        existing.setTeam(team);

        User teamAdmin = User.builder().id(5L).name("Lead").email("lead@example.com")
                .password("pw").appRole("USER").build();
        TeamMembership membership = TeamMembership.builder()
                .id(1L).user(teamAdmin).team(team).teamRole("TEAM_ADMIN").build();

        Decision saved = buildDecision();
        saved.setStatus(Decision.Status.APPROVED);

        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("lead@example.com")).thenReturn(Optional.of(teamAdmin));
        when(teamMembershipRepository.findByUserIdAndTeamId(5L, 1L)).thenReturn(Optional.of(membership));
        when(decisionRepository.save(any())).thenReturn(saved);

        DecisionDTO result = decisionService.updateStatus(1L, Decision.Status.APPROVED, null, "lead@example.com");
        assertThat(result.getStatus()).isEqualTo(Decision.Status.APPROVED);
    }

    @Test
    void updateStatus_appAdminCanApproveAnyTeam() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.PROPOSED);
        Decision saved = buildDecision();
        saved.setStatus(Decision.Status.APPROVED);
        mockGovernanceAsAppAdmin();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionRepository.save(any())).thenReturn(saved);

        DecisionDTO result = decisionService.updateStatus(1L, Decision.Status.APPROVED, null, "admin@example.com");
        assertThat(result.getStatus()).isEqualTo(Decision.Status.APPROVED);
    }

    @Test
    void update_approvedDecision_throwsBadRequestException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.APPROVED);
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class,
                () -> decisionService.update(1L, new UpdateDecisionRequest()));
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void update_rejectedDecision_throwsBadRequestException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.REJECTED);
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class,
                () -> decisionService.update(1L, new UpdateDecisionRequest()));
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void update_supersededDecision_throwsBadRequestException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.SUPERSEDED);
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class,
                () -> decisionService.update(1L, new UpdateDecisionRequest()));
        verify(decisionRepository, never()).save(any());
    }

    // --- delete ---

    @Test
    void delete_existingId_callsRepositoryDelete() {
        Decision existing = buildDecision();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        decisionService.delete(1L);

        verify(decisionRepository).delete(existing);
    }

    @Test
    void delete_nonExistingId_throwsResourceNotFoundException() {
        when(decisionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> decisionService.delete(99L));
        verify(decisionRepository, never()).delete(any());
    }

    @Test
    void delete_approvedDecision_throwsBadRequestException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.APPROVED);
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class, () -> decisionService.delete(1L));
        verify(decisionRepository, never()).delete(any());
    }

    @Test
    void delete_supersededDecision_throwsBadRequestException() {
        Decision existing = buildDecision();
        existing.setStatus(Decision.Status.SUPERSEDED);
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class, () -> decisionService.delete(1L));
        verify(decisionRepository, never()).delete(any());
    }
}
