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
    private LocalDateTime createdAt;
    private List<ReportItemDTO> items;
}
