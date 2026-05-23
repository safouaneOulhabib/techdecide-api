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
class DecisionRepositoryTest {

    @Autowired DecisionRepository decisionRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired UserRepository userRepository;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired TagRepository tagRepository;

    private Team team;
    private User user;

    @BeforeEach
    void setUp() {
        Organization org = organizationRepository.save(
                Organization.builder().name("Acme").build());
        team = teamRepository.save(
                Team.builder().name("Engineering").organization(org).build());
        user = userRepository.save(
                User.builder().name("Alice").email("alice@example.com")
                        .password("pw").role(User.Role.MEMBER).build());
    }

    private Decision saveDecision(String title, String context, String decisionText) {
        Decision d = Decision.builder()
                .title(title).context(context).decision(decisionText)
                .author(user).team(team)
                .tags(new ArrayList<>()).alternatives(new ArrayList<>())
                .build();
        return decisionRepository.save(d);
    }

    @Test
    void findByTeamId_returnsDecisionsForTeam() {
        saveDecision("Decision A", "Context A", "Choice A");
        saveDecision("Decision B", "Context B", "Choice B");

        List<Decision> results = decisionRepository.findByTeamId(team.getId());

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Decision::getTitle)
                .containsExactlyInAnyOrder("Decision A", "Decision B");
    }

    @Test
    void findByTeamId_differentTeam_returnsEmpty() {
        Organization org2 = organizationRepository.save(
                Organization.builder().name("Beta").build());
        Team otherTeam = teamRepository.save(
                Team.builder().name("Design").organization(org2).build());
        saveDecision("Decision A", "Context", "Choice");

        List<Decision> results = decisionRepository.findByTeamId(otherTeam.getId());

        assertThat(results).isEmpty();
    }

    @Test
    void findByTeamIdAndStatus_returnsOnlyMatchingStatus() {
        Decision d1 = saveDecision("Draft Decision", "Context", "Choice");
        assertThat(d1.getStatus()).isEqualTo(Decision.Status.DRAFT);

        List<Decision> drafts = decisionRepository.findByTeamIdAndStatus(
                team.getId(), Decision.Status.DRAFT);
        List<Decision> approved = decisionRepository.findByTeamIdAndStatus(
                team.getId(), Decision.Status.APPROVED);

        assertThat(drafts).hasSize(1);
        assertThat(approved).isEmpty();
    }

    @Test
    void findByAuthorId_returnsDecisionsForAuthor() {
        saveDecision("My Decision", "Context", "Choice");

        List<Decision> results = decisionRepository.findByAuthorId(user.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("My Decision");
    }

    @Test
    void findByAuthorId_differentAuthor_returnsEmpty() {
        User other = userRepository.save(
                User.builder().name("Bob").email("bob@example.com")
                        .password("pw").role(User.Role.MEMBER).build());
        saveDecision("Alice's Decision", "Context", "Choice");

        List<Decision> results = decisionRepository.findByAuthorId(other.getId());

        assertThat(results).isEmpty();
    }

    @Test
    void findByTagId_returnsDecisionsWithTag() {
        Tag tag = tagRepository.save(Tag.builder().name("backend").color("#f00").build());
        Decision d = saveDecision("Tagged Decision", "Context", "Choice");
        d.getTags().add(tag);
        decisionRepository.save(d);

        List<Decision> results = decisionRepository.findByTagId(tag.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Tagged Decision");
    }

    @Test
    void findByTagId_noDecisionsWithTag_returnsEmpty() {
        Tag tag = tagRepository.save(Tag.builder().name("frontend").color("#0f0").build());

        List<Decision> results = decisionRepository.findByTagId(tag.getId());

        assertThat(results).isEmpty();
    }

    @Test
    void searchByKeyword_matchesTitleCaseInsensitive() {
        saveDecision("Microservices Architecture", "Tech context", "Use micro");
        saveDecision("Monolith Refactor", "Legacy context", "Keep monolith");

        List<Decision> results = decisionRepository.searchByKeyword("micro");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Microservices Architecture");
    }

    @Test
    void searchByKeyword_matchesContextField() {
        saveDecision("Decision X", "We need a caching layer", "Use Redis");
        saveDecision("Decision Y", "Unrelated context", "Something else");

        List<Decision> results = decisionRepository.searchByKeyword("caching");

        assertThat(results).hasSize(1);
    }

    @Test
    void searchByKeyword_matchesDecisionField() {
        saveDecision("Decision Z", "Need storage", "Use PostgreSQL as primary store");

        List<Decision> results = decisionRepository.searchByKeyword("postgresql");

        assertThat(results).hasSize(1);
    }

    @Test
    void searchByKeyword_noMatch_returnsEmpty() {
        saveDecision("Use Redis", "Caching context", "Redis chosen");

        List<Decision> results = decisionRepository.searchByKeyword("kubernetes");

        assertThat(results).isEmpty();
    }

    @Test
    void searchByKeyword_matchesMultiple() {
        saveDecision("Use Kafka", "We need Kafka for messaging", "Kafka chosen");
        saveDecision("Use Kafka Streams", "Streaming with Kafka Streams", "Kafka Streams chosen");

        List<Decision> results = decisionRepository.searchByKeyword("kafka");

        assertThat(results).hasSize(2);
    }
}
