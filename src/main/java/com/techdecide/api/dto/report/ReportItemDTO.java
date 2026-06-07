package com.techdecide.api.dto.report;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReportItemDTO {

    private Long id;
    private Long originalDecisionId;
    private String decisionTitle;
    private String decisionStatus;
    private String decisionContext;
    private String decisionContent;
    private String decisionConsequences;
    private String decisionTeamName;
    private String decisionAuthorName;
    private LocalDateTime decisionCreatedAt;
    private List<AlternativeSnapshot> alternatives;
    private Integer position;

    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AlternativeSnapshot {
        private String name;
        private String rejectionReason;
    }
}
