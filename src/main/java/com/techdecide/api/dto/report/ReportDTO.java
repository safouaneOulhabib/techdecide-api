package com.techdecide.api.dto.report;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReportDTO {

    private Long id;
    private String title;
    private String introduction;
    private Long authorId;
    private String authorName;
    private Long projectId;
    private String projectName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ReportItemDTO> items;
}
