package com.techdecide.api.dto.project;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSummaryDTO {
    private Long id;
    private String name;
    private String organizationName;
    private int teamCount;
}
