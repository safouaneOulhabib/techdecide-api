package com.techdecide.api.dto.project;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTeamDTO {
    private Long teamId;
    private String teamName;
}
