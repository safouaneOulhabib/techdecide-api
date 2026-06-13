package com.techdecide.api.service;

import com.techdecide.api.dto.auth.AuthResponse;
import com.techdecide.api.dto.auth.LoginRequest;
import com.techdecide.api.dto.auth.RegisterRequest;
import com.techdecide.api.entity.TeamMembership;
import com.techdecide.api.entity.User;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.repository.TeamMembershipRepository;
import com.techdecide.api.repository.UserRepository;
import com.techdecide.api.security.JwtService;
import com.techdecide.api.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already exists");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .appRole("USER")
                .build();

        User savedUser = userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(
                savedUser.getId(), savedUser.getEmail(), savedUser.getPassword(),
                savedUser.getAppRole(), null, null
        );

        String token = jwtService.generateToken(principal);

        return AuthResponse.builder()
                .id(savedUser.getId())
                .token(token)
                .email(savedUser.getEmail())
                .name(savedUser.getName())
                .appRole(savedUser.getAppRole())
                .teamRole(null)
                .teamId(null)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        var membership = teamMembershipRepository.findByUserId(user.getId());
        String teamRole = membership.map(TeamMembership::getTeamRole).orElse(null);
        Long teamId = membership.map(m -> m.getTeam().getId()).orElse(null);

        UserPrincipal principal = new UserPrincipal(
                user.getId(), user.getEmail(), user.getPassword(),
                user.getAppRole(), teamRole, teamId
        );

        String token = jwtService.generateToken(principal);

        return AuthResponse.builder()
                .id(user.getId())
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .appRole(user.getAppRole())
                .teamRole(teamRole)
                .teamId(teamId)
                .build();
    }
}
