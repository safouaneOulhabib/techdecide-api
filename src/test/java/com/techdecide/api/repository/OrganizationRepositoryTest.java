package com.techdecide.api.repository;

import com.techdecide.api.entity.Organization;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class OrganizationRepositoryTest {

    @Autowired OrganizationRepository organizationRepository;

    private Organization save(String name) {
        return organizationRepository.save(
                Organization.builder().name(name).description("Desc for " + name).build());
    }

    @Test
    void findByName_existingName_returnsOrganization() {
        save("Acme");

        Optional<Organization> result = organizationRepository.findByName("Acme");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Acme");
    }

    @Test
    void findByName_nonExistingName_returnsEmpty() {
        Optional<Organization> result = organizationRepository.findByName("Unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void existsByName_existingName_returnsTrue() {
        save("Acme");

        assertThat(organizationRepository.existsByName("Acme")).isTrue();
    }

    @Test
    void existsByName_nonExistingName_returnsFalse() {
        assertThat(organizationRepository.existsByName("NoSuchOrg")).isFalse();
    }

    @Test
    void existsByName_caseSensitive_returnsFalseForWrongCase() {
        save("Acme");

        assertThat(organizationRepository.existsByName("acme")).isFalse();
    }

    @Test
    void findByName_createdAtIsSetAutomatically() {
        save("Acme");

        Organization found = organizationRepository.findByName("Acme").orElseThrow();

        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void save_uniqueConstraintViolation_throwsException() {
        save("Acme");

        Organization duplicate = Organization.builder().name("Acme").build();

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> {
                    organizationRepository.save(duplicate);
                    organizationRepository.flush();
                });
    }
}
