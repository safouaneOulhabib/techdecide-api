package com.techdecide.api.repository;

import com.techdecide.api.entity.ProjectTeam;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectTeamRepository extends JpaRepository<ProjectTeam, Long> {

    @EntityGraph(attributePaths = {"project", "team"})
    List<ProjectTeam> findByProjectId(Long projectId);

    @EntityGraph(attributePaths = {"project", "team"})
    List<ProjectTeam> findByTeamId(Long teamId);

    @EntityGraph(attributePaths = {"project", "team"})
    Optional<ProjectTeam> findByProjectIdAndTeamId(Long projectId, Long teamId);

    boolean existsByProjectIdAndTeamId(Long projectId, Long teamId);

    void deleteByProjectIdAndTeamId(Long projectId, Long teamId);

    void deleteAllByProjectId(Long projectId);
}
