package com.techdecide.api.dto.team;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AvailableUserDTO {
    private Long id;
    private String name;
    private String email;
}
