package com.techdecide.api.service;

import com.techdecide.api.dto.decision.CreateDecisionRequest;
import com.techdecide.api.dto.decision.DecisionDTO;
import com.techdecide.api.dto.decision.UpdateDecisionRequest;
import com.techdecide.api.dto.tag.TagDTO;
import com.techdecide.api.entity.*;
import com.techdecide.api.exception.BadRequestException;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DecisionService {

    private final DecisionRepository decisionRepository;
    private final DecisionTeamRepository decisionTeamRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final TeamMembershipRepository teamMembershipRepository;

    public DecisionDTO create(CreateDecisionRequest request, String authorEmail) {
        User author = loadUser(authorEmail);

        if (isAppAdmin(author)) {
            return createAsAppAdmin(request, author);
        }

        TeamMembership membership = teamMembershipRepository.findByUserId(author.getId())
                .orElseThrow(() -> new BadRequestException("You must be assigned to a team to create decisions"));

        Long actorTeamId = membership.getTeam().getId();

        // Actor's team must be assigned to the project
        if (!projectTeamRepository.existsByProjectIdAndTeamId(request.getProjectId(), actorTeamId)) {
            throw new ForbiddenException("Your team is not assigned to this project");
        }

        // All requested teamIds must be in the project
        for (Long teamId : request.getTeamIds()) {
            if (!projectTeamRepository.existsByProjectIdAndTeamId(request.getProjectId(), teamId)) {
                throw new BadRequestException("Team " + teamId + " is not assigned to this project");
            }
        }

        // Actor's own team must be among the selected teams
        if (!request.getTeamIds().contains(actorTeamId)) {
            throw new BadRequestException("Your team must be included in the involved teams");
        }

        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.getProjectId()));

        return buildAndSaveDecision(request, author, project);
    }

    private DecisionDTO createAsAppAdmin(CreateDecisionRequest request, User author) {
        // Validate all teamIds belong to the project
        for (Long teamId : request.getTeamIds()) {
            if (!projectTeamRepository.existsByProjectIdAndTeamId(request.getProjectId(), teamId)) {
                throw new BadRequestException("Team " + teamId + " is not assigned to this project");
            }
        }
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.getProjectId()));
        return buildAndSaveDecision(request, author, project);
    }

    private DecisionDTO buildAndSaveDecision(CreateDecisionRequest request, User author, Project project) {
        List<Tag> tags = request.getTagIds() != null
                ? tagRepository.findAllById(request.getTagIds())
                : List.of();

        List<Alternative> alternatives = request.getAlternatives() != null
                ? request.getAlternatives().stream()
                .map(alt -> Alternative.builder()
                        .name(alt.getName())
                        .rejectionReason(alt.getRejectionReason())
                        .build())
                .collect(Collectors.toList())
                : List.of();

        Decision decision = Decision.builder()
                .title(request.getTitle())
                .context(request.getContext())
                .decision(request.getDecision())
                .consequences(request.getConsequences())
                .author(author)
                .project(project)
                .tags(tags)
                .alternatives(alternatives)
                .reviewDate(request.getReviewDate())
                .build();

        alternatives.forEach(alt -> alt.setDecision(decision));
        Decision saved = decisionRepository.save(decision);

        // Create DecisionTeam rows
        List<DecisionTeam> decisionTeams = request.getTeamIds().stream()
                .map(teamId -> {
                    Team team = new Team();
                    team.setId(teamId);
                    return DecisionTeam.builder()
                            .decision(saved)
                            .team(team)
                            .build();
                })
                .collect(Collectors.toList());
        decisionTeamRepository.saveAll(decisionTeams);
        saved.setDecisionTeams(decisionTeams);

        return mapToDTO(saved, author);
    }

    public List<DecisionDTO> getAll(String actorEmail) {
        User actor = loadUser(actorEmail);
        if (isAppAdmin(actor)) {
            return decisionRepository.findAll().stream()
                    .map(d -> mapToDTO(d, actor))
                    .collect(Collectors.toList());
        }
        Long teamId = getTeamId(actor);
        if (teamId == null) {
            return List.of();
        }
        return decisionRepository.findVisibleToTeam(teamId).stream()
                .map(d -> mapToDTO(d, actor))
                .collect(Collectors.toList());
    }

    public DecisionDTO getById(Long id, String actorEmail) {
        Decision decision = decisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Decision", id));
        User actor = loadUser(actorEmail);
        if (!isAppAdmin(actor)) {
            assertActorInProject(actor, decision.getProject().getId());
        }
        return mapToDTO(decision, actor);
    }

    public List<DecisionDTO> search(String keyword) {
        return decisionRepository.searchByKeyword(keyword).stream()
                .map(d -> mapToDTO(d, null))
                .collect(Collectors.toList());
    }

    public DecisionDTO update(Long id, UpdateDecisionRequest request, String actorEmail) {
        Decision decision = decisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Decision", id));

        if (decision.getStatus() != Decision.Status.DRAFT && decision.getStatus() != Decision.Status.PROPOSED) {
            throw new BadRequestException("Cannot edit a decision in status: " + decision.getStatus());
        }

        User actor = loadUser(actorEmail);
        if (!isAppAdmin(actor)) {
            assertActorOnInvolvedTeam(actor, decision.getId());
        }

        if (request.getTitle() != null) decision.setTitle(request.getTitle());
        if (request.getContext() != null) decision.setContext(request.getContext());
        if (request.getDecision() != null) decision.setDecision(request.getDecision());
        if (request.getConsequences() != null) decision.setConsequences(request.getConsequences());
        if (request.getReviewDate() != null) decision.setReviewDate(request.getReviewDate());

        if (request.getTagIds() != null) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            decision.setTags(tags);
        }

        if (request.getAlternatives() != null) {
            decision.getAlternatives().clear();
            List<Alternative> alts = request.getAlternatives().stream()
                    .map(alt -> Alternative.builder()
                            .name(alt.getName())
                            .rejectionReason(alt.getRejectionReason())
                            .decision(decision)
                            .build())
                    .collect(Collectors.toList());
            decision.getAlternatives().addAll(alts);
        }

        Decision updated = decisionRepository.save(decision);
        return mapToDTO(updated, actor);
    }

    public DecisionDTO updateStatus(Long id, Decision.Status newStatus, Long supersededById, String actorEmail) {
        Decision decision = decisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Decision", id));

        if (!decision.getStatus().canTransitionTo(newStatus)) {
            throw new ConflictException("Cannot transition from " + decision.getStatus() + " to " + newStatus);
        }

        User actor = loadUser(actorEmail);

        if (!isAppAdmin(actor)) {
            // Must be in the project to see the decision at all
            assertActorInProject(actor, decision.getProject().getId());

            boolean onInvolvedTeam = isActorOnInvolvedTeam(actor, decision.getId());

            if (!onInvolvedTeam) {
                throw new ForbiddenException("You must be a member of an involved team to change decision status");
            }

            // MEMBER can only DRAFT → PROPOSED
            Long actorTeamId = getTeamId(actor);
            TeamMembership membership = teamMembershipRepository
                    .findByUserId(actor.getId())
                    .orElseThrow(() -> new ForbiddenException("No team membership found"));

            if ("MEMBER".equals(membership.getTeamRole())) {
                if (decision.getStatus() != Decision.Status.DRAFT || newStatus != Decision.Status.PROPOSED) {
                    throw new ForbiddenException("Members can only propose decisions (DRAFT → PROPOSED)");
                }
            }
            // TEAM_ADMIN of any involved team → full governance (already verified onInvolvedTeam above)
        }

        if (newStatus == Decision.Status.SUPERSEDED) {
            if (supersededById == null) {
                throw new BadRequestException("supersededById is required when superseding a decision");
            }
            if (supersededById.equals(id)) {
                throw new BadRequestException("A decision cannot supersede itself");
            }
            Decision superseding = decisionRepository.findById(supersededById)
                    .orElseThrow(() -> new ResourceNotFoundException("Decision", supersededById));
            if (superseding.getStatus() != Decision.Status.APPROVED) {
                throw new BadRequestException(
                        "The superseding decision must be in APPROVED status (was: " + superseding.getStatus() + ")");
            }
            decision.setSupersededBy(superseding);
        }

        decision.setStatus(newStatus);
        return mapToDTO(decisionRepository.save(decision), actor);
    }

    public void delete(Long id, String actorEmail) {
        Decision decision = decisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Decision", id));

        if (decision.getStatus() == Decision.Status.APPROVED || decision.getStatus() == Decision.Status.SUPERSEDED) {
            throw new BadRequestException("Cannot delete a decision in status: " + decision.getStatus());
        }

        User actor = loadUser(actorEmail);
        if (!isAppAdmin(actor)) {
            assertActorOnInvolvedTeam(actor, decision.getId());
        }

        decisionRepository.delete(decision);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User loadUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private boolean isAppAdmin(User user) {
        return "APP_ADMIN".equals(user.getAppRole());
    }

    private Long getTeamId(User user) {
        return teamMembershipRepository.findByUserId(user.getId())
                .map(tm -> tm.getTeam().getId())
                .orElse(null);
    }

    private boolean isActorInProject(User actor, Long projectId) {
        Long teamId = getTeamId(actor);
        if (teamId == null) return false;
        return projectTeamRepository.existsByProjectIdAndTeamId(projectId, teamId);
    }

    private void assertActorInProject(User actor, Long projectId) {
        if (!isActorInProject(actor, projectId)) {
            throw new ForbiddenException("You are not a member of a team assigned to this project");
        }
    }

    private boolean isActorOnInvolvedTeam(User actor, Long decisionId) {
        Long teamId = getTeamId(actor);
        if (teamId == null) return false;
        return decisionTeamRepository.existsByDecisionIdAndTeamId(decisionId, teamId);
    }

    private void assertActorOnInvolvedTeam(User actor, Long decisionId) {
        if (!isActorOnInvolvedTeam(actor, decisionId)) {
            throw new ForbiddenException("You must be a member of an involved team for this decision");
        }
    }

    private boolean isActorTeamAdminOfInvolvedTeam(User actor, Long decisionId) {
        Long teamId = getTeamId(actor);
        if (teamId == null) return false;
        if (!decisionTeamRepository.existsByDecisionIdAndTeamId(decisionId, teamId)) return false;
        return teamMembershipRepository.findByUserId(actor.getId())
                .map(m -> "TEAM_ADMIN".equals(m.getTeamRole()))
                .orElse(false);
    }

    private DecisionDTO mapToDTO(Decision decision, User actor) {
        List<DecisionTeam> decisionTeams = decisionTeamRepository.findByDecisionId(decision.getId());

        boolean canVote = false;
        boolean canGovern = false;

        if (actor != null) {
            if (isAppAdmin(actor)) {
                canVote = true;
                canGovern = true;
            } else {
                canVote = isActorOnInvolvedTeam(actor, decision.getId());
                canGovern = isActorTeamAdminOfInvolvedTeam(actor, decision.getId());
            }
        }

        return DecisionDTO.builder()
                .id(decision.getId())
                .title(decision.getTitle())
                .context(decision.getContext())
                .decision(decision.getDecision())
                .consequences(decision.getConsequences())
                .status(decision.getStatus())
                .supersededById(decision.getSupersededBy() != null ? decision.getSupersededBy().getId() : null)
                .supersededByTitle(decision.getSupersededBy() != null ? decision.getSupersededBy().getTitle() : null)
                .authorId(decision.getAuthor().getId())
                .authorName(decision.getAuthor().getName())
                .projectId(decision.getProject().getId())
                .projectName(decision.getProject().getName())
                .teams(decisionTeams.stream()
                        .map(dt -> DecisionDTO.TeamRef.builder()
                                .teamId(dt.getTeam().getId())
                                .teamName(dt.getTeam().getName())
                                .build())
                        .collect(Collectors.toList()))
                .tags(decision.getTags().stream()
                        .map(tag -> TagDTO.builder()
                                .id(tag.getId())
                                .name(tag.getName())
                                .color(tag.getColor())
                                .build())
                        .collect(Collectors.toList()))
                .alternatives(decision.getAlternatives().stream()
                        .map(alt -> DecisionDTO.AlternativeDTO.builder()
                                .id(alt.getId())
                                .name(alt.getName())
                                .rejectionReason(alt.getRejectionReason())
                                .build())
                        .collect(Collectors.toList()))
                .reviewDate(decision.getReviewDate())
                .createdAt(decision.getCreatedAt())
                .updatedAt(decision.getUpdatedAt())
                .canVote(canVote)
                .canGovern(canGovern)
                .build();
    }
}
