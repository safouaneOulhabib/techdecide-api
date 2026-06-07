package com.techdecide.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "report_items")
public class ReportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "original_decision_id")
    private Long originalDecisionId;

    @Column(name = "decision_title", nullable = false)
    private String decisionTitle;

    @Column(name = "decision_status", nullable = false)
    private String decisionStatus;

    @Column(name = "decision_context", columnDefinition = "TEXT")
    private String decisionContext;

    @Column(name = "decision_content", columnDefinition = "TEXT")
    private String decisionContent;

    @Column(name = "decision_consequences", columnDefinition = "TEXT")
    private String decisionConsequences;

    @Column(name = "decision_team_name")
    private String decisionTeamName;

    @Column(name = "decision_author_name")
    private String decisionAuthorName;

    @Column(name = "decision_created_at")
    private LocalDateTime decisionCreatedAt;

    @Column(name = "alternatives_json", columnDefinition = "TEXT")
    private String alternativesJson;

    @Column(name = "position")
    private Integer position;
}
