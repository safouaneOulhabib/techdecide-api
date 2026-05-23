package com.techdecide.api.dto.organization;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OrganizationDTO {

    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
}
