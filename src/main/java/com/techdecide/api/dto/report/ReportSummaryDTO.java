package com.techdecide.api.dto.report;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReportSummaryDTO {

    private Long id;
    private String title;
    private String authorName;
    private LocalDateTime createdAt;
    private int itemCount;
}
