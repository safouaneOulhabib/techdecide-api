package com.techdecide.api.dto.team;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeamMemberDTO {
    private Long userId;
    private String name;
    private String email;
    private String role;
    private Long teamId;
}
