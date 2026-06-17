package com.techdecide.api.service;

import com.techdecide.api.dto.comment.CommentDTO;
import com.techdecide.api.dto.comment.CreateCommentRequest;
import com.techdecide.api.entity.*;
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
class CommentServiceTest {
    // QA matrix coverage:
    // COM-01 COM-02 COM-03 COM-04 COM-06 COM-07 COM-08 VIS-05

    @Mock private CommentRepository commentRepository;
    @Mock private DecisionRepository decisionRepository;
    @Mock private UserRepository userRepository;
    @Mock private TeamMembershipRepository teamMembershipRepository;
    @Mock private ProjectTeamRepository projectTeamRepository;
    @InjectMocks private CommentService commentService;

    private User buildUser() {
        return User.builder().id(1L).name("Alice").email("alice@example.com")
                .password("pw").appRole("USER").build();
    }

    private Team buildTeam() {
        return Team.builder().id(1L).name("Eng")
                .organization(Organization.builder().id(1L).name("Acme").build()).build();
    }

    private Project buildProject() {
        return Project.builder().id(1L).name("GTN")
                .organization(Organization.builder().id(1L).name("Acme").build()).build();
    }

    private Decision buildDecision() {
        Decision d = Decision.builder()
                .id(1L).title("Use PostgreSQL").context("We need a DB")
                .decision("PostgreSQL chosen").status(Decision.Status.DRAFT)
                .author(buildUser())
                .project(buildProject())
                .tags(new ArrayList<>()).alternatives(new ArrayList<>())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        d.setDecisionTeams(new ArrayList<>());
        return d;
    }

    private TeamMembership buildMembership(User user, Team team) {
        return TeamMembership.builder().id(1L).user(user).team(team).teamRole("MEMBER").build();
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
    void create_projectMember_returnsCommentDTO() {
        User alice = buildUser();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(buildDecision()));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(teamMembershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(buildMembership(alice, buildTeam())));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
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
        User alice = buildUser();
        CreateCommentRequest req = new CreateCommentRequest();
        req.setContent("No vote");
        req.setVote(null);
        Comment comment = Comment.builder().id(2L).content("No vote").vote(null)
                .author(alice).decision(buildDecision())
                .createdAt(LocalDateTime.now()).build();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(buildDecision()));
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(alice));
        when(teamMembershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(buildMembership(alice, buildTeam())));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
        when(commentRepository.save(any())).thenReturn(comment);

        CommentDTO result = commentService.create(1L, req, "alice@example.com");

        assertThat(result.getVote()).isNull();
    }

    @Test
    void create_noTeam_throwsForbiddenException() {
        User bob = User.builder().id(2L).name("Bob").email("bob@example.com")
                .password("pw").appRole("USER").build();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(buildDecision()));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(bob));
        when(teamMembershipRepository.findByUserId(2L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> commentService.create(1L, buildRequest(), "bob@example.com"));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void create_teamNotInProject_throwsForbiddenException() {
        User bob = User.builder().id(2L).name("Bob").email("bob@example.com")
                .password("pw").appRole("USER").build();
        Team otherTeam = Team.builder().id(3L).name("Other").build();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(buildDecision()));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(bob));
        when(teamMembershipRepository.findByUserId(2L))
                .thenReturn(Optional.of(buildMembership(bob, otherTeam)));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 3L)).thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> commentService.create(1L, buildRequest(), "bob@example.com"));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void create_appAdmin_skipsProjectCheck() {
        User admin = User.builder().id(99L).name("Admin").email("admin@example.com")
                .password("pw").appRole("APP_ADMIN").build();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(buildDecision()));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(commentRepository.save(any())).thenReturn(buildComment());

        commentService.create(1L, buildRequest(), "admin@example.com");

        verify(teamMembershipRepository, never()).findByUserId(any());
        verify(projectTeamRepository, never()).existsByProjectIdAndTeamId(any(), any());
    }

    @Test
    void create_differentProjectMember_canComment() {
        // Any project member (even non-involved-team member) can comment
        User alice = buildUser();
        when(decisionRepository.findById(1L)).thenReturn(Optional.of(buildDecision()));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(teamMembershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(buildMembership(alice, buildTeam())));
        when(projectTeamRepository.existsByProjectIdAndTeamId(1L, 1L)).thenReturn(true);
        when(commentRepository.save(any())).thenReturn(buildComment());

        CommentDTO result = commentService.create(1L, buildRequest(), "alice@example.com");
        assertThat(result).isNotNull();
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
    void delete_ownerRequest_callsRepositoryDelete() {
        Comment existing = buildComment();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buildUser()));

        commentService.delete(1L, "alice@example.com");

        verify(commentRepository).delete(existing);
    }

    @Test
    void delete_nonOwnerRequest_throwsForbiddenException() {
        Comment existing = buildComment();
        User other = User.builder().id(2L).name("Bob").email("bob@example.com")
                .password("pw").appRole("USER").build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(other));

        assertThrows(ForbiddenException.class, () -> commentService.delete(1L, "bob@example.com"));
        verify(commentRepository, never()).delete(any());
    }

    @Test
    void delete_nonExistingId_throwsResourceNotFoundException() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> commentService.delete(99L, "alice@example.com"));
        verify(commentRepository, never()).delete(any());
    }
}
