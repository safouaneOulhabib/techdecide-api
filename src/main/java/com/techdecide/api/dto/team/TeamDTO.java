package com.techdecide.api.dto.team;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TeamDTO {

    private Long id;
    private String name;
    private Long organizationId;
    private String organizationName;
    private LocalDateTime createdAt;
}
