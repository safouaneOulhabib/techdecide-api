package com.techdecide.api.repository;

import com.techdecide.api.entity.Alternative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlternativeRepository extends JpaRepository<Alternative, Long> {

    List<Alternative> findByDecisionId(Long decisionId);
}