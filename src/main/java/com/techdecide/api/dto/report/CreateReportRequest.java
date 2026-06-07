package com.techdecide.api.dto.report;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateReportRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String introduction;

    private List<Long> decisionIds;
}
