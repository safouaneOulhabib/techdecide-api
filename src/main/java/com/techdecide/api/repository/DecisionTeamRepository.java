package com.techdecide.api.repository;

import com.techdecide.api.entity.DecisionTeam;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DecisionTeamRepository extends JpaRepository<DecisionTeam, Long> {

    @EntityGraph(attributePaths = {"decision", "team"})
    List<DecisionTeam> findByDecisionId(Long decisionId);

    @EntityGraph(attributePaths = {"decision", "team"})
    List<DecisionTeam> findByTeamId(Long teamId);

    boolean existsByDecisionIdAndTeamId(Long decisionId, Long teamId);

    void deleteByDecisionId(Long decisionId);
}
