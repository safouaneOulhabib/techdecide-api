package com.techdecide.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdecide.api.dto.report.*;
import com.techdecide.api.entity.Decision;
import com.techdecide.api.entity.Project;
import com.techdecide.api.entity.ProjectTeam;
import com.techdecide.api.entity.Report;
import com.techdecide.api.entity.ReportItem;
import com.techdecide.api.entity.User;
import com.techdecide.api.exception.BadRequestException;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.DecisionRepository;
import com.techdecide.api.repository.ProjectRepository;
import com.techdecide.api.repository.ProjectTeamRepository;
import com.techdecide.api.repository.ReportRepository;
import com.techdecide.api.repository.TeamMembershipRepository;
import com.techdecide.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    private final ReportRepository reportRepository;
    private final DecisionRepository decisionRepository;
    private final UserRepository userRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final ObjectMapper objectMapper;

    public ReportDTO create(CreateReportRequest request, String authorEmail) {
        if (request.getProjectId() == null) {
            throw new BadRequestException("projectId is required");
        }
        if (request.getDecisionIds() == null || request.getDecisionIds().isEmpty()) {
            throw new BadRequestException("A report must contain at least one decision");
        }

        User author = userRepository.findByEmail(authorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", null));

        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.getProjectId()));

        if (!"APP_ADMIN".equals(author.getAppRole())) {
            Long actorTeamId = teamMembershipRepository.findByUserId(author.getId())
                    .map(tm -> tm.getTeam().getId())
                    .orElseThrow(() -> new BadRequestException("You must be assigned to a team to create reports"));

            if (!projectTeamRepository.existsByProjectIdAndTeamId(request.getProjectId(), actorTeamId)) {
                throw new ForbiddenException("Your team is not assigned to this project");
            }
        }

        List<ReportItem> items = new ArrayList<>();
        for (int i = 0; i < request.getDecisionIds().size(); i++) {
            Long decisionId = request.getDecisionIds().get(i);
            Decision decision = decisionRepository.findById(decisionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Decision", decisionId));

            if (!request.getProjectId().equals(decision.getProject().getId())) {
                throw new BadRequestException(
                        "All decisions must belong to the same project. Decision " + decisionId
                        + " belongs to project " + decision.getProject().getId()
                        + ", expected project " + request.getProjectId());
            }

            ReportItem item = ReportItem.builder()
                    .originalDecisionId(decision.getId())
                    .decisionTitle(decision.getTitle())
                    .decisionStatus(decision.getStatus().name())
                    .decisionContext(decision.getContext())
                    .decisionContent(decision.getDecision())
                    .decisionConsequences(decision.getConsequences())
                    .decisionTeamName(project.getName())
                    .decisionAuthorName(decision.getAuthor() != null ? decision.getAuthor().getName() : null)
                    .decisionCreatedAt(decision.getCreatedAt())
                    .alternativesJson(serializeAlternatives(decision))
                    .position(i)
                    .build();
            items.add(item);
        }

        Report report = Report.builder()
                .title(request.getTitle())
                .introduction(request.getIntroduction())
                .author(author)
                .project(project)
                .projectName(project.getName())
                .items(items)
                .build();

        items.forEach(item -> item.setReport(report));

        Report saved = reportRepository.save(report);
        return mapToDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<ReportSummaryDTO> getAll(String actorEmail) {
        User actor = userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if ("APP_ADMIN".equals(actor.getAppRole())) {
            return reportRepository.findAll().stream()
                    .map(this::mapToSummaryDTO)
                    .collect(Collectors.toList());
        }

        Long actorTeamId = teamMembershipRepository.findByUserId(actor.getId())
                .map(tm -> tm.getTeam().getId())
                .orElse(null);

        if (actorTeamId == null) {
            return List.of();
        }

        List<Long> projectIds = projectTeamRepository.findByTeamId(actorTeamId).stream()
                .map(pt -> pt.getProject().getId())
                .collect(Collectors.toList());

        if (projectIds.isEmpty()) {
            return List.of();
        }

        return reportRepository.findByProjectIdIn(projectIds).stream()
                .map(this::mapToSummaryDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReportDTO getById(Long id, String actorEmail) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report", id));

        User actor = userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!"APP_ADMIN".equals(actor.getAppRole())) {
            Long actorTeamId = teamMembershipRepository.findByUserId(actor.getId())
                    .map(tm -> tm.getTeam().getId())
                    .orElse(null);
            if (actorTeamId == null) {
                throw new ForbiddenException("You must be assigned to a team to view reports");
            }
            if (!projectTeamRepository.existsByProjectIdAndTeamId(report.getProject().getId(), actorTeamId)) {
                throw new ForbiddenException("You are not a member of this report's project");
            }
        }

        return mapToDTO(report);
    }

    public ReportDTO update(Long id, UpdateReportRequest request, String requesterEmail) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report", id));

        if (!report.getAuthor().getEmail().equals(requesterEmail)) {
            throw new ForbiddenException("Only the author can edit this report");
        }

        boolean changed = false;
        if (request.getTitle() != null && !request.getTitle().equals(report.getTitle())) {
            report.setTitle(request.getTitle());
            changed = true;
        }
        if (request.getIntroduction() != null && !request.getIntroduction().equals(report.getIntroduction())) {
            report.setIntroduction(request.getIntroduction());
            changed = true;
        }

        if (!changed) {
            return mapToDTO(report);
        }

        Report saved = reportRepository.saveAndFlush(report);
        return mapToDTO(saved);
    }

    public void delete(Long id, String requesterEmail) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report", id));

        if (!report.getAuthor().getEmail().equals(requesterEmail)) {
            throw new ForbiddenException("Only the author can delete this report");
        }

        reportRepository.delete(report);
    }

    private String serializeAlternatives(Decision decision) {
        if (decision.getAlternatives() == null || decision.getAlternatives().isEmpty()) {
            return "[]";
        }
        try {
            List<ReportItemDTO.AlternativeSnapshot> snapshots = decision.getAlternatives().stream()
                    .map(alt -> ReportItemDTO.AlternativeSnapshot.builder()
                            .name(alt.getName())
                            .rejectionReason(alt.getRejectionReason())
                            .build())
                    .collect(Collectors.toList());
            return objectMapper.writeValueAsString(snapshots);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize alternatives for decision id={}: {}",
                    decision.getId(), e.getMessage());
            return "[]";
        }
    }

    private List<ReportItemDTO.AlternativeSnapshot> deserializeAlternatives(String json, Long itemId) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    new TypeReference<List<ReportItemDTO.AlternativeSnapshot>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize alternatives_json for report_item id={}, raw value=[{}]: {}",
                    itemId, json, e.getMessage());
            return List.of();
        }
    }

    private ReportDTO mapToDTO(Report report) {
        List<ReportItemDTO> itemDTOs = report.getItems().stream()
                .map(this::mapItemToDTO)
                .collect(Collectors.toList());

        return ReportDTO.builder()
                .id(report.getId())
                .title(report.getTitle())
                .introduction(report.getIntroduction())
                .authorId(report.getAuthor().getId())
                .authorName(report.getAuthor().getName())
                .projectId(report.getProject() != null ? report.getProject().getId() : null)
                .projectName(report.getProjectName())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .items(itemDTOs)
                .build();
    }

    private ReportItemDTO mapItemToDTO(ReportItem item) {
        return ReportItemDTO.builder()
                .id(item.getId())
                .originalDecisionId(item.getOriginalDecisionId())
                .decisionTitle(item.getDecisionTitle())
                .decisionStatus(item.getDecisionStatus())
                .decisionContext(item.getDecisionContext())
                .decisionContent(item.getDecisionContent())
                .decisionConsequences(item.getDecisionConsequences())
                .decisionTeamName(item.getDecisionTeamName())
                .decisionAuthorName(item.getDecisionAuthorName())
                .decisionCreatedAt(item.getDecisionCreatedAt())
                .alternatives(deserializeAlternatives(item.getAlternativesJson(), item.getId()))
                .position(item.getPosition())
                .build();
    }

    private ReportSummaryDTO mapToSummaryDTO(Report report) {
        List<ReportItem> items = report.getItems() != null ? report.getItems() : List.of();
        Map<String, Long> statusCounts = items.stream()
                .collect(Collectors.groupingBy(ReportItem::getDecisionStatus, Collectors.counting()));
        return ReportSummaryDTO.builder()
                .id(report.getId())
                .title(report.getTitle())
                .authorId(report.getAuthor().getId())
                .authorName(report.getAuthor().getName())
                .projectId(report.getProject() != null ? report.getProject().getId() : null)
                .projectName(report.getProjectName())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .itemCount(items.size())
                .statusCounts(statusCounts)
                .build();
    }
}
