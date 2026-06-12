package com.techdecide.api.dto.team;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignMemberRequest {

    @NotNull(message = "userId is required")
    private Long userId;
}
