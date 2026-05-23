package com.techdecide.api.repository;

import com.techdecide.api.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByOrganizationId(Long organizationId);

    boolean existsByNameAndOrganizationId(String name, Long organizationId);
}