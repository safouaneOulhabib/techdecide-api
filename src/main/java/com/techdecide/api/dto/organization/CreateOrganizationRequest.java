package com.techdecide.api.dto.organization;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrganizationRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;
}
