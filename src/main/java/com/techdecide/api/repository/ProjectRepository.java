package com.techdecide.api.repository;

import com.techdecide.api.entity.Project;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    @EntityGraph(attributePaths = {"organization"})
    List<Project> findAllByOrganizationId(Long orgId);

    @EntityGraph(attributePaths = {"organization"})
    List<Project> findAll();

    @EntityGraph(attributePaths = {"organization"})
    Optional<Project> findById(Long id);
}
