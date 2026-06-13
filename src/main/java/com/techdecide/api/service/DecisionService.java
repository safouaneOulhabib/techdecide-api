package com.techdecide.api.service;

import com.techdecide.api.dto.decision.CreateDecisionRequest;
import com.techdecide.api.dto.decision.DecisionDTO;
import com.techdecide.api.dto.decision.UpdateDecisionRequest;
import com.techdecide.api.dto.tag.TagDTO;
import com.techdecide.api.entity.Alternative;
import com.techdecide.api.entity.Decision;
import com.techdecide.api.entity.Tag;
import com.techdecide.api.entity.Team;
import com.techdecide.api.entity.TeamMembership;
import com.techdecide.api.entity.User;
import com.techdecide.api.exception.BadRequestException;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.DecisionRepository;
import com.techdecide.api.repository.TagRepository;
import com.techdecide.api.repository.TeamMembershipRepository;
import com.techdecide.api.repository.TeamRepository;
import com.techdecide.api.repository.UserRepository;
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
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final TeamMembershipRepository teamMembershipRepository;

    public DecisionDTO create(CreateDecisionRequest request, String authorEmail) {
        User author = loadUser(authorEmail);

        Long teamId;
        if (isAppAdmin(author)) {
            teamId = request.getTeamId();
        } else {
            TeamMembership membership = teamMembershipRepository.findByUserId(author.getId())
                    .orElseThrow(() -> new BadRequestException(
                            "You must be assigned to a team to create decisions"));
            teamId = membership.getTeam().getId();
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", teamId));

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
                .team(team)
                .tags(tags)
                .alternatives(alternatives)
                .reviewDate(request.getReviewDate())
                .build();

        alternatives.forEach(alt -> alt.setDecision(decision));

        Decision saved = decisionRepository.save(decision);
        return mapToDTO(saved);
    }

    public List<DecisionDTO> getAll(String actorEmail) {
        User actor = loadUser(actorEmail);
        if (isAppAdmin(actor)) {
            return decisionRepository.findAll()
                    .stream()
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());
        }
        Long teamId = getTeamId(actor);
        if (teamId == null) {
            return List.of();
        }
        return decisionRepository.findByTeamId(teamId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public DecisionDTO getById(Long id, String actorEmail) {
        Decision decision = decisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Decision", id));
        User actor = loadUser(actorEmail);
        if (!isAppAdmin(actor)) {
            Long actorTeamId = getTeamId(actor);
            if (actorTeamId == null || !actorTeamId.equals(decision.getTeam().getId())) {
                throw new ForbiddenException("You can only view decisions in your team");
            }
        }
        return mapToDTO(decision);
    }

    public List<DecisionDTO> getByTeam(Long teamId) {
        return decisionRepository.findByTeamId(teamId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<DecisionDTO> search(String keyword) {
        return decisionRepository.searchByKeyword(keyword)
                .stream()
                .map(this::mapToDTO)
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
            Long actorTeamId = getTeamId(actor);
            if (actorTeamId == null || !actorTeamId.equals(decision.getTeam().getId())) {
                throw new ForbiddenException("You can only edit decisions in your team");
            }
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
            List<Alternative> alternatives = request.getAlternatives().stream()
                    .map(alt -> Alternative.builder()
                            .name(alt.getName())
                            .rejectionReason(alt.getRejectionReason())
                            .decision(decision)
                            .build())
                    .collect(Collectors.toList());
            decision.getAlternatives().addAll(alternatives);
        }

        Decision updated = decisionRepository.save(decision);
        return mapToDTO(updated);
    }

    public DecisionDTO updateStatus(Long id, Decision.Status newStatus, Long supersededById, String actorEmail) {
        Decision decision = decisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Decision", id));

        if (!decision.getStatus().canTransitionTo(newStatus)) {
            throw new ConflictException("Cannot transition from " + decision.getStatus() + " to " + newStatus);
        }

        User actor = loadUser(actorEmail);
        if (!isAppAdmin(actor)) {
            TeamMembership membership = teamMembershipRepository
                    .findByUserIdAndTeamId(actor.getId(), decision.getTeam().getId())
                    .orElseThrow(() -> new ForbiddenException(
                            "You must be a member of this team to manage decisions"));

            if ("MEMBER".equals(membership.getTeamRole())) {
                if (decision.getStatus() != Decision.Status.DRAFT || newStatus != Decision.Status.PROPOSED) {
                    throw new ForbiddenException("Members can only propose decisions (DRAFT → PROPOSED)");
                }
            }
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
        return mapToDTO(decisionRepository.save(decision));
    }

    public void delete(Long id, String actorEmail) {
        Decision decision = decisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Decision", id));

        if (decision.getStatus() == Decision.Status.APPROVED || decision.getStatus() == Decision.Status.SUPERSEDED) {
            throw new BadRequestException("Cannot delete a decision in status: " + decision.getStatus());
        }

        User actor = loadUser(actorEmail);
        if (!isAppAdmin(actor)) {
            Long actorTeamId = getTeamId(actor);
            if (actorTeamId == null || !actorTeamId.equals(decision.getTeam().getId())) {
                throw new ForbiddenException("You can only delete decisions in your team");
            }
        }

        decisionRepository.delete(decision);
    }

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

    private DecisionDTO mapToDTO(Decision decision) {
        return DecisionDTO.builder()
                .id(decision.getId())
                .title(decision.getTitle())
                .context(decision.getContext())
                .decision(decision.getDecision())
                .consequences(decision.getConsequences())
                .status(decision.getStatus())
                .supersededById(decision.getSupersededBy() != null ? decision.getSupersededBy().getId() : null)
                .supersededByTitle(decision.getSupersededBy() != null ? decision.getSupersededBy().getTitle() : null)
                .authorName(decision.getAuthor().getName())
                .teamName(decision.getTeam().getName())
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
                .build();
    }
}
