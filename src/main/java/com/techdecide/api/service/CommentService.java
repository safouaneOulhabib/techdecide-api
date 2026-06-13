package com.techdecide.api.service;

import com.techdecide.api.dto.comment.CommentDTO;
import com.techdecide.api.dto.comment.CreateCommentRequest;
import com.techdecide.api.entity.Comment;
import com.techdecide.api.entity.Decision;
import com.techdecide.api.entity.User;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.CommentRepository;
import com.techdecide.api.repository.DecisionRepository;
import com.techdecide.api.repository.TeamMembershipRepository;
import com.techdecide.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final DecisionRepository decisionRepository;
    private final UserRepository userRepository;
    private final TeamMembershipRepository teamMembershipRepository;

    public CommentDTO create(Long decisionId, CreateCommentRequest request, String authorEmail) {
        Decision decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> new ResourceNotFoundException("Decision", decisionId));

        User author = userRepository.findByEmail(authorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", null));

        if (!"APP_ADMIN".equals(author.getAppRole())) {
            teamMembershipRepository.findByUserIdAndTeamId(author.getId(), decision.getTeam().getId())
                    .orElseThrow(() -> new ForbiddenException("You must be a member of this team to comment"));
        }

        Comment comment = Comment.builder()
                .content(request.getContent())
                .vote(request.getVote())
                .decision(decision)
                .author(author)
                .build();

        Comment saved = commentRepository.save(comment);
        return mapToDTO(saved);
    }

    public List<CommentDTO> getByDecision(Long decisionId) {
        if (!decisionRepository.existsById(decisionId)) {
            throw new ResourceNotFoundException("Decision", decisionId);
        }
        return commentRepository.findByDecisionId(decisionId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public void delete(Long id, String requesterEmail) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", id));
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", null));
        if (!comment.getAuthor().getId().equals(requester.getId())) {
            throw new ForbiddenException("You are not the author of this comment");
        }
        commentRepository.delete(comment);
    }

    private CommentDTO mapToDTO(Comment comment) {
        return CommentDTO.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .vote(comment.getVote())
                .authorId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getName())
                .decisionId(comment.getDecision().getId())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
