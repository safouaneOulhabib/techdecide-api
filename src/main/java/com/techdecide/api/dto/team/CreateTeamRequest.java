package com.techdecide.api.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTeamRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Organization ID is required")
    private Long organizationId;
}
