package com.techdecide.api.dto.team;

import lombok.Data;

@Data
public class UpdateTeamRequest {

    private String name;
    private Long organizationId;
}
