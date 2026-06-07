package com.techdecide.api.repository;

import com.techdecide.api.entity.ReportItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportItemRepository extends JpaRepository<ReportItem, Long> {
}
