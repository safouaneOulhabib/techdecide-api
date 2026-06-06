package com.techdecide.api.repository;

import com.techdecide.api.entity.Decision;
import com.techdecide.api.entity.Decision.Status;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DecisionRepository extends JpaRepository<Decision, Long> {

    @Override
    @EntityGraph(attributePaths = {"supersededBy"})
    List<Decision> findAll();

    @EntityGraph(attributePaths = {"supersededBy"})
    List<Decision> findByTeamId(Long teamId);

    List<Decision> findByTeamIdAndStatus(Long teamId, Status status);

    List<Decision> findByAuthorId(Long authorId);

    @Query("SELECT d FROM Decision d JOIN d.tags t WHERE t.id = :tagId")
    List<Decision> findByTagId(@Param("tagId") Long tagId);

    @EntityGraph(attributePaths = {"supersededBy"})
    @Query("SELECT d FROM Decision d WHERE " +
            "LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.context) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.decision) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Decision> searchByKeyword(@Param("keyword") String keyword);
}