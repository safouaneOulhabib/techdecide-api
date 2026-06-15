package com.techdecide.api.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateReportRequest {

    @NotNull(message = "projectId is required")
    private Long projectId;

    @NotBlank(message = "Title is required")
    private String title;

    private String introduction;

    private List<Long> decisionIds;
}
