package com.techdecide.api.service;

import com.techdecide.api.dto.decision.CreateDecisionRequest;
import com.techdecide.api.dto.decision.DecisionDTO;
import com.techdecide.api.dto.decision.UpdateDecisionRequest;
import com.techdecide.api.dto.tag.TagDTO;
import com.techdecide.api.entity.Alternative;
import com.techdecide.api.entity.Decision;
import com.techdecide.api.entity.Tag;
import com.techdecide.api.entity.Team;
import com.techdecide.api.entity.User;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.DecisionRepository;
import com.techdecide.api.repository.TagRepository;
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

    public DecisionDTO create(CreateDecisionRequest request, String authorEmail) {
        User author = userRepository.findByEmail(authorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", null));

        Team team = teamRepository.findById(request.getTeamId())
                .orElseThrow(() -> new ResourceNotFoundException("Team", request.getTeamId()));

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

    public List<DecisionDTO> getAll() {
        return decisionRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public DecisionDTO getById(Long id) {
        Decision decision = decisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Decision", id));
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

    public DecisionDTO update(Long id, UpdateDecisionRequest request) {
        Decision decision = decisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Decision", id));

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

    public DecisionDTO updateStatus(Long id, Decision.Status status) {
        Decision decision = decisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Decision", id));
        decision.setStatus(status);
        return mapToDTO(decisionRepository.save(decision));
    }

    public void delete(Long id) {
        Decision decision = decisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Decision", id));
        decisionRepository.delete(decision);
    }

    private DecisionDTO mapToDTO(Decision decision) {
        return DecisionDTO.builder()
                .id(decision.getId())
                .title(decision.getTitle())
                .context(decision.getContext())
                .decision(decision.getDecision())
                .consequences(decision.getConsequences())
                .status(decision.getStatus())
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