package com.techdecide.api.repository;

import com.techdecide.api.entity.Report;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    @EntityGraph(attributePaths = {"author", "project", "items"})
    List<Report> findByProjectIdIn(List<Long> projectIds);
}
