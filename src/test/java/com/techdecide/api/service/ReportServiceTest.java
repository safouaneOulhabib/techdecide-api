package com.techdecide.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdecide.api.dto.report.CreateReportRequest;
import com.techdecide.api.dto.report.ReportDTO;
import com.techdecide.api.dto.report.ReportItemDTO;
import com.techdecide.api.dto.report.UpdateReportRequest;
import com.techdecide.api.entity.*;
import com.techdecide.api.exception.BadRequestException;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.DecisionRepository;
import com.techdecide.api.repository.ReportRepository;
import com.techdecide.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private DecisionRepository decisionRepository;
    @Mock private UserRepository userRepository;
    @Spy  private ObjectMapper objectMapper;

    @InjectMocks private ReportService reportService;

    private User author;
    private User otherUser;
    private Team team;
    private Organization org;

    @BeforeEach
    void setUp() {
        org = Organization.builder().id(1L).name("Acme").build();
        team = Team.builder().id(1L).name("Engineering").organization(org).build();
        author = User.builder().id(1L).name("Alice").email("alice@example.com")
                .password("pw").role(User.Role.MEMBER).build();
        otherUser = User.builder().id(2L).name("Bob").email("bob@example.com")
                .password("pw").role(User.Role.MEMBER).build();
    }

    private Decision buildDecision(Long id, String title, Decision.Status status) {
        return Decision.builder()
                .id(id)
                .title(title)
                .context("Some context")
                .decision("Some decision")
                .consequences("Some consequences")
                .status(status)
                .author(author)
                .team(team)
                .alternatives(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Report buildReport(Long id, List<ReportItem> items) {
        Report report = Report.builder()
                .id(id)
                .title("My Report")
                .introduction("Intro text")
                .author(author)
                .createdAt(LocalDateTime.now())
                .items(items != null ? new ArrayList<>(items) : new ArrayList<>())
                .build();
        if (report.getItems() != null) {
            report.getItems().forEach(item -> item.setReport(report));
        }
        return report;
    }

    private ReportItem buildItem(Long id, Long decisionId, String decisionTitle, String status, int position) {
        return ReportItem.builder()
                .id(id)
                .originalDecisionId(decisionId)
                .decisionTitle(decisionTitle)
                .decisionStatus(status)
                .decisionContext("Some context")
                .decisionContent("Some decision")
                .decisionConsequences("Some consequences")
                .decisionTeamName("Engineering")
                .decisionAuthorName("Alice")
                .decisionCreatedAt(LocalDateTime.now())
                .alternativesJson("[]")
                .position(position)
                .build();
    }

    private CreateReportRequest buildCreateRequest(List<Long> decisionIds) {
        CreateReportRequest req = new CreateReportRequest();
        req.setTitle("My Report");
        req.setIntroduction("Intro text");
        req.setDecisionIds(decisionIds);
        return req;
    }

    // --- create: status mix ---

    @Test
    void create_withMixOfStatuses_allSucceedAndStatusesPreservedAsStrings() {
        Decision draft = buildDecision(1L, "Draft Decision", Decision.Status.DRAFT);
        Decision proposed = buildDecision(2L, "Proposed Decision", Decision.Status.PROPOSED);
        Decision approved = buildDecision(3L, "Approved Decision", Decision.Status.APPROVED);
        Decision rejected = buildDecision(4L, "Rejected Decision", Decision.Status.REJECTED);
        Decision superseded = buildDecision(5L, "Superseded Decision", Decision.Status.SUPERSEDED);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(author));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(decisionRepository.findById(2L)).thenReturn(Optional.of(proposed));
        when(decisionRepository.findById(3L)).thenReturn(Optional.of(approved));
        when(decisionRepository.findById(4L)).thenReturn(Optional.of(rejected));
        when(decisionRepository.findById(5L)).thenReturn(Optional.of(superseded));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        when(reportRepository.save(captor.capture())).thenAnswer(inv -> {
            Report r = captor.getValue();
            r.setId(10L);
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });

        ReportDTO result = reportService.create(
                buildCreateRequest(List.of(1L, 2L, 3L, 4L, 5L)), "alice@example.com");

        assertThat(result.getAuthorId()).isEqualTo(1L);
        assertThat(result.getItems()).hasSize(5);
        assertThat(result.getItems()).extracting(ReportItemDTO::getDecisionStatus)
                .containsExactly("DRAFT", "PROPOSED", "APPROVED", "REJECTED", "SUPERSEDED");
    }

    // --- create: empty/null decisionIds ---

    @Test
    void create_emptyDecisionIds_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> reportService.create(buildCreateRequest(List.of()), "alice@example.com"));
        verify(reportRepository, never()).save(any());
    }

    @Test
    void create_nullDecisionIds_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> reportService.create(buildCreateRequest(null), "alice@example.com"));
        verify(reportRepository, never()).save(any());
    }

    // --- create: non-existent decision ---

    @Test
    void create_nonExistentDecisionId_throwsResourceNotFound() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(author));
        when(decisionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reportService.create(buildCreateRequest(List.of(99L)), "alice@example.com"));
        verify(reportRepository, never()).save(any());
    }

    // --- create: alternatives JSON ---

    @Test
    void create_persistsAlternativesAsJsonAndDTOReturnsTypedList() {
        Decision decision = buildDecision(1L, "Use PostgreSQL", Decision.Status.APPROVED);
        Alternative alt1 = Alternative.builder().id(1L).name("MySQL").rejectionReason("Less features").build();
        Alternative alt2 = Alternative.builder().id(2L).name("MongoDB").rejectionReason("NoSQL not suitable").build();
        decision.setAlternatives(List.of(alt1, alt2));

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(author));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(decision));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        when(reportRepository.save(captor.capture())).thenAnswer(inv -> {
            Report r = captor.getValue();
            r.setId(1L);
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });

        ReportDTO result = reportService.create(buildCreateRequest(List.of(1L)), "alice@example.com");

        assertThat(result.getItems()).hasSize(1);
        List<ReportItemDTO.AlternativeSnapshot> alts = result.getItems().get(0).getAlternatives();
        assertThat(alts).hasSize(2);
        assertThat(alts.get(0).getName()).isEqualTo("MySQL");
        assertThat(alts.get(0).getRejectionReason()).isEqualTo("Less features");
        assertThat(alts.get(1).getName()).isEqualTo("MongoDB");
        assertThat(alts.get(1).getRejectionReason()).isEqualTo("NoSQL not suitable");

        // Verify the raw JSON was stored
        String storedJson = captor.getValue().getItems().get(0).getAlternativesJson();
        assertThat(storedJson).contains("MySQL").contains("MongoDB");
    }

    // --- update ---

    @Test
    void update_byAuthor_succeeds_onlyTitleAndIntroductionChange() {
        ReportItem existingItem = buildItem(1L, 10L, "Original Decision", "APPROVED", 0);
        Report report = buildReport(1L, List.of(existingItem));

        UpdateReportRequest req = new UpdateReportRequest();
        req.setTitle("Updated Title");
        req.setIntroduction("Updated Intro");

        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(reportRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ReportDTO result = reportService.update(1L, req, "alice@example.com");

        assertThat(result.getTitle()).isEqualTo("Updated Title");
        assertThat(result.getIntroduction()).isEqualTo("Updated Intro");
        assertThat(result.getAuthorId()).isEqualTo(1L);
        assertThat(result.getAuthorName()).isEqualTo("Alice");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getDecisionTitle()).isEqualTo("Original Decision");
    }

    @Test
    void update_byNonAuthor_throwsForbidden() {
        Report report = buildReport(1L, List.of());

        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        assertThrows(ForbiddenException.class,
                () -> reportService.update(1L, new UpdateReportRequest(), "bob@example.com"));
        verify(reportRepository, never()).save(any());
    }

    @Test
    void update_withNewTitle_savesAndUpdatedAtIsSetByPreUpdate() {
        Report report = buildReport(1L, List.of());
        assertThat(report.getUpdatedAt()).isNull();

        UpdateReportRequest req = new UpdateReportRequest();
        req.setTitle("New Title");

        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(reportRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            // Simulate @PreUpdate that JPA fires on saveAndFlush
            r.setUpdatedAt(LocalDateTime.now());
            return r;
        });

        ReportDTO result = reportService.update(1L, req, "alice@example.com");

        verify(reportRepository).saveAndFlush(report);
        // The response DTO must reflect updatedAt, not null — this was the bug
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void update_withSameValues_doesNotSaveAndUpdatedAtRemainsNull() {
        Report report = buildReport(1L, List.of()); // title="My Report", introduction="Intro text"

        UpdateReportRequest req = new UpdateReportRequest();
        req.setTitle("My Report");
        req.setIntroduction("Intro text");

        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        ReportDTO result = reportService.update(1L, req, "alice@example.com");

        verify(reportRepository, never()).saveAndFlush(any());
        assertThat(result.getUpdatedAt()).isNull();
    }

    @Test
    void update_withNullFields_doesNotSaveAndUpdatedAtRemainsNull() {
        Report report = buildReport(1L, List.of());

        // PUT body with no fields set — nothing to change
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        ReportDTO result = reportService.update(1L, new UpdateReportRequest(), "alice@example.com");

        verify(reportRepository, never()).saveAndFlush(any());
        assertThat(result.getUpdatedAt()).isNull();
    }

    // --- delete ---

    @Test
    void delete_byAuthor_succeeds() {
        Report report = buildReport(1L, List.of());
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        reportService.delete(1L, "alice@example.com");

        verify(reportRepository).delete(report);
    }

    @Test
    void delete_byNonAuthor_throwsForbidden() {
        Report report = buildReport(1L, List.of());
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        assertThrows(ForbiddenException.class,
                () -> reportService.delete(1L, "bob@example.com"));
        verify(reportRepository, never()).delete(any());
    }

    // --- snapshot independence: edit original decision ---

    @Test
    void snapshotIndependence_editOriginalDecision_reportSnapshotUnchanged() {
        Decision decision = buildDecision(1L, "Original Title", Decision.Status.APPROVED);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(author));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(decision));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        when(reportRepository.save(captor.capture())).thenAnswer(inv -> {
            Report r = captor.getValue();
            r.setId(1L);
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });

        ReportDTO created = reportService.create(buildCreateRequest(List.of(1L)), "alice@example.com");
        assertThat(created.getItems().get(0).getDecisionTitle()).isEqualTo("Original Title");
        assertThat(created.getItems().get(0).getDecisionStatus()).isEqualTo("APPROVED");

        // Simulate editing the original decision after report creation
        decision.setTitle("Updated Title After Report");
        decision.setStatus(Decision.Status.SUPERSEDED);

        // The saved report snapshot still holds the original values
        Report savedReport = captor.getValue();
        assertThat(savedReport.getItems().get(0).getDecisionTitle()).isEqualTo("Original Title");
        assertThat(savedReport.getItems().get(0).getDecisionStatus()).isEqualTo("APPROVED");
    }

    // --- snapshot independence: delete original decision ---

    @Test
    void snapshotIndependence_deleteOriginalDecision_reportStillLoadsCorrectly() {
        ReportItem item = buildItem(1L, 10L, "Deleted Decision", "DRAFT", 0);
        Report report = buildReport(1L, List.of(item));

        // Report loads fine even though the original decision (id=10) no longer exists
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        ReportDTO result = reportService.getById(1L);

        assertThat(result.getAuthorId()).isEqualTo(1L);
        assertThat(result.getAuthorName()).isEqualTo("Alice");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getOriginalDecisionId()).isEqualTo(10L);
        assertThat(result.getItems().get(0).getDecisionTitle()).isEqualTo("Deleted Decision");
        assertThat(result.getItems().get(0).getDecisionStatus()).isEqualTo("DRAFT");
    }

    // --- position ordering ---

    @Test
    void create_preservesOrderOfDecisionIds() {
        Decision d1 = buildDecision(1L, "First", Decision.Status.DRAFT);
        Decision d2 = buildDecision(2L, "Second", Decision.Status.APPROVED);
        Decision d3 = buildDecision(3L, "Third", Decision.Status.REJECTED);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(author));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(d1));
        when(decisionRepository.findById(2L)).thenReturn(Optional.of(d2));
        when(decisionRepository.findById(3L)).thenReturn(Optional.of(d3));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        when(reportRepository.save(captor.capture())).thenAnswer(inv -> {
            Report r = captor.getValue();
            r.setId(1L);
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });

        ReportDTO result = reportService.create(
                buildCreateRequest(List.of(1L, 2L, 3L)), "alice@example.com");

        assertThat(result.getItems().get(0).getDecisionTitle()).isEqualTo("First");
        assertThat(result.getItems().get(0).getPosition()).isEqualTo(0);
        assertThat(result.getItems().get(1).getDecisionTitle()).isEqualTo("Second");
        assertThat(result.getItems().get(1).getPosition()).isEqualTo(1);
        assertThat(result.getItems().get(2).getDecisionTitle()).isEqualTo("Third");
        assertThat(result.getItems().get(2).getPosition()).isEqualTo(2);
    }

    // --- malformed alternatives_json ---

    @Test
    void getById_malformedAlternativesJson_returnsEmptyListAndDoesNotThrow() {
        ReportItem item = buildItem(1L, 10L, "Some Decision", "APPROVED", 0);
        item.setAlternativesJson("not-valid-json{{{");
        Report report = buildReport(1L, List.of(item));

        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        // Must not throw — bad JSON falls back to empty alternatives list
        ReportDTO result = reportService.getById(1L);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getAlternatives()).isEmpty();
    }

    // --- empty alternatives round-trip ---

    @Test
    void create_decisionWithNoAlternatives_alternativesJsonIsEmptyArrayAndDTOAlternativesIsEmptyList() {
        Decision decision = buildDecision(1L, "No-Alt Decision", Decision.Status.DRAFT);
        // alternatives already empty from buildDecision

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(author));
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(decision));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        when(reportRepository.save(captor.capture())).thenAnswer(inv -> {
            Report r = captor.getValue();
            r.setId(1L);
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });

        ReportDTO result = reportService.create(buildCreateRequest(List.of(1L)), "alice@example.com");

        // Raw column value must be "[]", not null
        String storedJson = captor.getValue().getItems().get(0).getAlternativesJson();
        assertThat(storedJson).isEqualTo("[]");

        // DTO alternatives must be an empty list, not null
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getAlternatives()).isNotNull().isEmpty();
    }
}
