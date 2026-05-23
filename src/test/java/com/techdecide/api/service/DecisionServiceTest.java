package com.techdecide.api.service;

import com.techdecide.api.dto.decision.CreateDecisionRequest;
import com.techdecide.api.dto.decision.DecisionDTO;
import com.techdecide.api.dto.decision.UpdateDecisionRequest;
import com.techdecide.api.entity.*;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.DecisionRepository;
import com.techdecide.api.repository.TagRepository;
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
    @InjectMocks private DecisionService decisionService;

    private User buildUser() {
        return User.builder().id(1L).name("Alice").email("alice@example.com")
                .password("pw").role(User.Role.MEMBER).build();
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
        Decision existing = buildDecision();
        Decision updated = buildDecision();
        updated.setStatus(Decision.Status.APPROVED);
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(decisionRepository.save(any())).thenReturn(updated);

        DecisionDTO result = decisionService.updateStatus(1L, Decision.Status.APPROVED);

        assertThat(result.getStatus()).isEqualTo(Decision.Status.APPROVED);
    }

    @Test
    void updateStatus_nonExistingId_throwsResourceNotFoundException() {
        when(decisionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> decisionService.updateStatus(99L, Decision.Status.APPROVED));
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
}
