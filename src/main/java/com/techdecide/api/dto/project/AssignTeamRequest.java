package com.techdecide.api.dto.project;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssignTeamRequest {

    @NotNull(message = "teamId is required")
    private Long teamId;
}
