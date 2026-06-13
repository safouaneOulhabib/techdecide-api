package com.techdecide.api.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private Long id;
    private String token;
    private String email;
    private String name;
    private String appRole;
    private String teamRole;
    private Long teamId;
}
