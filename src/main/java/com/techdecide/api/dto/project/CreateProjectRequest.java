package com.techdecide.api.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    @NotNull(message = "organizationId is required")
    private Long organizationId;
}
