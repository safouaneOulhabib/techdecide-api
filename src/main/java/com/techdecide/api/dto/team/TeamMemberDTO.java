package com.techdecide.api.dto.team;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TeamMemberDTO {
    private Long userId;
    private String name;
    private String email;
    private String teamRole;
    private Long teamId;
    private LocalDateTime createdAt;
}
