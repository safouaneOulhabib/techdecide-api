package com.techdecide.api.repository;

import com.techdecide.api.entity.Organization;
import com.techdecide.api.entity.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class TeamRepositoryTest {

    @Autowired TeamRepository teamRepository;
    @Autowired OrganizationRepository organizationRepository;

    private Organization org1;
    private Organization org2;

    @BeforeEach
    void setUp() {
        org1 = organizationRepository.save(Organization.builder().name("Acme").build());
        org2 = organizationRepository.save(Organization.builder().name("Beta").build());
    }

    private Team saveTeam(String name, Organization org) {
        return teamRepository.save(Team.builder().name(name).organization(org).build());
    }

    @Test
    void findByOrganizationId_returnsTeamsForOrg() {
        saveTeam("Engineering", org1);
        saveTeam("Design", org1);
        saveTeam("Marketing", org2);

        List<Team> results = teamRepository.findByOrganizationId(org1.getId());

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Team::getName)
                .containsExactlyInAnyOrder("Engineering", "Design");
    }

    @Test
    void findByOrganizationId_noTeams_returnsEmpty() {
        List<Team> results = teamRepository.findByOrganizationId(org1.getId());

        assertThat(results).isEmpty();
    }

    @Test
    void existsByNameAndOrganizationId_existingCombination_returnsTrue() {
        saveTeam("Engineering", org1);

        assertThat(teamRepository.existsByNameAndOrganizationId("Engineering", org1.getId())).isTrue();
    }

    @Test
    void existsByNameAndOrganizationId_sameNameDifferentOrg_returnsFalse() {
        saveTeam("Engineering", org1);

        assertThat(teamRepository.existsByNameAndOrganizationId("Engineering", org2.getId())).isFalse();
    }

    @Test
    void existsByNameAndOrganizationId_nonExistingName_returnsFalse() {
        saveTeam("Engineering", org1);

        assertThat(teamRepository.existsByNameAndOrganizationId("Design", org1.getId())).isFalse();
    }

    @Test
    void findByOrganizationId_createdAtIsSetAutomatically() {
        saveTeam("Engineering", org1);

        List<Team> results = teamRepository.findByOrganizationId(org1.getId());

        assertThat(results.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    void findByOrganizationId_sameNameInDifferentOrgs_isolatesCorrectly() {
        saveTeam("Engineering", org1);
        saveTeam("Engineering", org2);

        List<Team> org1Teams = teamRepository.findByOrganizationId(org1.getId());
        List<Team> org2Teams = teamRepository.findByOrganizationId(org2.getId());

        assertThat(org1Teams).hasSize(1);
        assertThat(org2Teams).hasSize(1);
    }
}
