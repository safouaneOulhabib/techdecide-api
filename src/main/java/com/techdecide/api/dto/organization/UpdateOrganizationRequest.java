package com.techdecide.api.dto.organization;

import lombok.Data;

@Data
public class UpdateOrganizationRequest {

    private String name;
    private String description;
}
