package com.techdecide.api.dto.project;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDTO {
    private Long id;
    private String name;
    private String description;
    private Long organizationId;
    private String organizationName;
    private int teamCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
