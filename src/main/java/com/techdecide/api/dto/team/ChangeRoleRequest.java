package com.techdecide.api.dto.team;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeRoleRequest {

    @NotBlank(message = "role is required")
    private String role;
}
