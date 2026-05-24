package com.techdecide.api.service;

import com.techdecide.api.dto.comment.CommentDTO;
import com.techdecide.api.dto.comment.CreateCommentRequest;
import com.techdecide.api.entity.*;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.CommentRepository;
import com.techdecide.api.repository.DecisionRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private DecisionRepository decisionRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private CommentService commentService;

    private User buildUser() {
        return User.builder().id(1L).name("Alice").email("alice@example.com")
                .password("pw").role(User.Role.MEMBER).build();
    }

    private Decision buildDecision() {
        return Decision.builder()
                .id(1L).title("Use PostgreSQL").context("We need a DB")
                .decision("PostgreSQL chosen").status(Decision.Status.DRAFT)
                .author(buildUser())
                .team(Team.builder().id(1L).name("Eng")
                        .organization(Organization.builder().id(1L).name("Acme").build()).build())
                .tags(new ArrayList<>()).alternatives(new ArrayList<>())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private Comment buildComment() {
        return Comment.builder()
                .id(1L).content("Great decision!").vote(Comment.Vote.APPROVE)
                .author(buildUser()).decision(buildDecision())
                .createdAt(LocalDateTime.now()).build();
    }

    private CreateCommentRequest buildRequest() {
        CreateCommentRequest req = new CreateCommentRequest();
        req.setContent("Great decision!");
        req.setVote(Comment.Vote.APPROVE);
        return req;
    }

    // --- create ---

    @Test
    void create_validRequest_returnsCommentDTO() {
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(buildDecision()));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildUser()));
        when(commentRepository.save(any())).thenReturn(buildComment());

        CommentDTO result = commentService.create(1L, buildRequest(), "alice@example.com");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getContent()).isEqualTo("Great decision!");
        assertThat(result.getVote()).isEqualTo(Comment.Vote.APPROVE);
        assertThat(result.getAuthorName()).isEqualTo("Alice");
        assertThat(result.getDecisionId()).isEqualTo(1L);
    }

    @Test
    void create_decisionNotFound_throwsResourceNotFoundException() {
        when(decisionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> commentService.create(99L, buildRequest(), "alice@example.com"));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void create_userNotFound_throwsResourceNotFoundException() {
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(buildDecision()));
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> commentService.create(1L, buildRequest(), "nobody@example.com"));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void create_withNullVote_savesWithNullVote() {
        CreateCommentRequest req = new CreateCommentRequest();
        req.setContent("No vote");
        req.setVote(null);
        Comment comment = Comment.builder().id(2L).content("No vote").vote(null)
                .author(buildUser()).decision(buildDecision())
                .createdAt(LocalDateTime.now()).build();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(buildDecision()));
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(buildUser()));
        when(commentRepository.save(any())).thenReturn(comment);

        CommentDTO result = commentService.create(1L, req, "alice@example.com");

        assertThat(result.getVote()).isNull();
    }

    @Test
    void create_savesCommentLinkedToCorrectDecisionAndAuthor() {
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(buildDecision()));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildUser()));
        when(commentRepository.save(any())).thenReturn(buildComment());

        commentService.create(1L, buildRequest(), "alice@example.com");

        verify(commentRepository).save(argThat(c ->
                c.getContent().equals("Great decision!")
                && c.getDecision().getId().equals(1L)
                && c.getAuthor().getEmail().equals("alice@example.com")));
    }

    // --- getByDecision ---

    @Test
    void getByDecision_existingDecision_returnsMappedComments() {
        when(decisionRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.findByDecisionId(1L)).thenReturn(List.of(buildComment()));

        List<CommentDTO> result = commentService.getByDecision(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("Great decision!");
    }

    @Test
    void getByDecision_decisionNotFound_throwsResourceNotFoundException() {
        when(decisionRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> commentService.getByDecision(99L));
        verify(commentRepository, never()).findByDecisionId(any());
    }

    @Test
    void getByDecision_noComments_returnsEmptyList() {
        when(decisionRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.findByDecisionId(1L)).thenReturn(List.of());

        List<CommentDTO> result = commentService.getByDecision(1L);

        assertThat(result).isEmpty();
    }

    // --- delete ---

    @Test
    void delete_existingId_callsRepositoryDelete() {
        Comment existing = buildComment();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(existing));

        commentService.delete(1L);

        verify(commentRepository).delete(existing);
    }

    @Test
    void delete_nonExistingId_throwsResourceNotFoundException() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> commentService.delete(99L));
        verify(commentRepository, never()).delete(any());
    }
}
