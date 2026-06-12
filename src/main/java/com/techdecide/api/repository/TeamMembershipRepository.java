package com.techdecide.api.repository;

import com.techdecide.api.entity.TeamMembership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMembershipRepository extends JpaRepository<TeamMembership, Long> {

    @EntityGraph(attributePaths = {"user", "team"})
    List<TeamMembership> findByTeamId(Long teamId);

    @EntityGraph(attributePaths = {"user", "team"})
    Optional<TeamMembership> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "team"})
    Optional<TeamMembership> findByUserIdAndTeamId(Long userId, Long teamId);

    boolean existsByUserIdAndTeamId(Long userId, Long teamId);

    boolean existsByTeamIdAndTeamRoleAndUserIdNot(Long teamId, String teamRole, Long userId);
}
