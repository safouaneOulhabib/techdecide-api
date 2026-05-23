package com.techdecide.api.dto.tag;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTagRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String color;
}
