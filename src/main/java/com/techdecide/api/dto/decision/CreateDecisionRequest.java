package com.techdecide.api.dto.decision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateDecisionRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Context is required")
    private String context;

    @NotBlank(message = "Decision is required")
    private String decision;

    private String consequences;

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotEmpty(message = "At least one team ID is required")
    private List<Long> teamIds;

    private List<Long> tagIds;

    private List<AlternativeRequest> alternatives;

    private LocalDateTime reviewDate;

    @Data
    public static class AlternativeRequest {
        @NotBlank(message = "Alternative name is required")
        private String name;
        private String rejectionReason;
    }
}
