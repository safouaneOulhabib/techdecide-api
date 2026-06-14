package com.techdecide.api.repository;

import com.techdecide.api.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class CommentRepositoryTest {

    @Autowired CommentRepository commentRepository;
    @Autowired DecisionRepository decisionRepository;
    @Autowired UserRepository userRepository;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired ProjectRepository projectRepository;
    // TeamRepository no longer needed — decisions scope to projects now

    private User alice;
    private User bob;
    private Decision decision;

    @BeforeEach
    void setUp() {
        Organization org = organizationRepository.save(
                Organization.builder().name("Acme").build());
        Project project = projectRepository.save(
                Project.builder().name("GTN").organization(org).build());

        alice = userRepository.save(User.builder().name("Alice").email("alice@example.com")
                .password("pw").appRole("USER").build());
        bob = userRepository.save(User.builder().name("Bob").email("bob@example.com")
                .password("pw").appRole("USER").build());

        Decision d = Decision.builder()
                .title("Use PostgreSQL").context("Context").decision("Choice")
                .author(alice).project(project)
                .tags(new ArrayList<>()).alternatives(new ArrayList<>()).build();
        d.setDecisionTeams(new ArrayList<>());
        decision = decisionRepository.save(d);
    }

    private Comment saveComment(String content, Comment.Vote vote, User author, Decision d) {
        return commentRepository.save(Comment.builder()
                .content(content).vote(vote).author(author).decision(d).build());
    }

    @Test
    void findByDecisionId_returnsCommentsForDecision() {
        saveComment("Great idea!", Comment.Vote.APPROVE, alice, decision);
        saveComment("I agree", Comment.Vote.APPROVE, bob, decision);

        List<Comment> results = commentRepository.findByDecisionId(decision.getId());

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Comment::getContent)
                .containsExactlyInAnyOrder("Great idea!", "I agree");
    }

    @Test
    void findByDecisionId_noComments_returnsEmpty() {
        List<Comment> results = commentRepository.findByDecisionId(decision.getId());

        assertThat(results).isEmpty();
    }

    @Test
    void findByDecisionId_differentDecision_returnsEmpty() {
        Organization org2 = organizationRepository.save(
                Organization.builder().name("Beta").build());
        Project project2 = projectRepository.save(
                Project.builder().name("Other Project").organization(org2).build());
        Decision other = Decision.builder()
                .title("Other").context("C").decision("D")
                .author(alice).project(project2)
                .tags(new ArrayList<>()).alternatives(new ArrayList<>()).build();
        other.setDecisionTeams(new ArrayList<>());
        Decision otherDecision = decisionRepository.save(other);

        saveComment("Comment on first", null, alice, decision);

        List<Comment> results = commentRepository.findByDecisionId(otherDecision.getId());

        assertThat(results).isEmpty();
    }

    @Test
    void findByAuthorId_returnsCommentsForAuthor() {
        saveComment("Alice's comment", Comment.Vote.APPROVE, alice, decision);
        saveComment("Bob's comment", Comment.Vote.REJECT, bob, decision);

        List<Comment> results = commentRepository.findByAuthorId(alice.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getContent()).isEqualTo("Alice's comment");
    }

    @Test
    void findByAuthorId_noComments_returnsEmpty() {
        List<Comment> results = commentRepository.findByAuthorId(alice.getId());

        assertThat(results).isEmpty();
    }

    @Test
    void save_createdAtIsSetAutomatically() {
        Comment saved = saveComment("Test", null, alice, decision);

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void save_withNullVote_savesSuccessfully() {
        Comment saved = saveComment("Neutral comment", null, alice, decision);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVote()).isNull();
    }

    @Test
    void findByDecisionId_commentsHaveCorrectVotes() {
        saveComment("Approve", Comment.Vote.APPROVE, alice, decision);
        saveComment("Reject", Comment.Vote.REJECT, bob, decision);

        List<Comment> results = commentRepository.findByDecisionId(decision.getId());

        assertThat(results).extracting(Comment::getVote)
                .containsExactlyInAnyOrder(Comment.Vote.APPROVE, Comment.Vote.REJECT);
    }
}
