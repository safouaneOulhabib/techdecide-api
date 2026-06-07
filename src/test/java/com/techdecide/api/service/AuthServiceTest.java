package com.techdecide.api.service;

import com.techdecide.api.dto.auth.AuthResponse;
import com.techdecide.api.dto.auth.LoginRequest;
import com.techdecide.api.dto.auth.RegisterRequest;
import com.techdecide.api.entity.User;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.repository.UserRepository;
import com.techdecide.api.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @InjectMocks private AuthService authService;

    private RegisterRequest buildRegisterRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setName("John Doe");
        req.setEmail("john@example.com");
        req.setPassword("password123");
        return req;
    }

    private LoginRequest buildLoginRequest() {
        LoginRequest req = new LoginRequest();
        req.setEmail("john@example.com");
        req.setPassword("password123");
        return req;
    }

    private User buildUser() {
        return User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .password("encoded-password")
                .role(User.Role.MEMBER)
                .build();
    }

    @Test
    void register_validRequest_returnsAuthResponse() {
        RegisterRequest request = buildRegisterRequest();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(buildUser());
        when(jwtService.generateToken(any())).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
        assertThat(response.getName()).isEqualTo("John Doe");
        assertThat(response.getRole()).isEqualTo("MEMBER");
    }

    @Test
    void register_emailAlreadyExists_throwsConflictException() {
        RegisterRequest request = buildRegisterRequest();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThrows(ConflictException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void register_savesEncodedPassword() {
        RegisterRequest request = buildRegisterRequest();
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(buildUser());
        when(jwtService.generateToken(any())).thenReturn("token");

        authService.register(request);

        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(argThat(u -> u.getPassword().equals("encoded")));
    }

    @Test
    void register_newUserHasMemberRole() {
        RegisterRequest request = buildRegisterRequest();
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(buildUser());
        when(jwtService.generateToken(any())).thenReturn("token");

        authService.register(request);

        verify(userRepository).save(argThat(u -> u.getRole() == User.Role.MEMBER));
    }

    @Test
    void login_validCredentials_returnsAuthResponse() {
        LoginRequest request = buildLoginRequest();
        User user = buildUser();
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any())).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
        assertThat(response.getName()).isEqualTo("John Doe");
        assertThat(response.getRole()).isEqualTo("MEMBER");
    }

    @Test
    void login_authenticatesWithCorrectCredentials() {
        LoginRequest request = buildLoginRequest();
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(buildUser()));
        when(jwtService.generateToken(any())).thenReturn("token");

        authService.login(request);

        verify(authenticationManager).authenticate(
                argThat(auth -> auth instanceof UsernamePasswordAuthenticationToken
                        && auth.getPrincipal().equals("john@example.com"))
        );
    }

    @Test
    void login_badCredentials_throwsException() {
        LoginRequest request = buildLoginRequest();
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void login_userNotFoundAfterAuth_throwsRuntimeException() {
        LoginRequest request = buildLoginRequest();
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> authService.login(request));
    }

    @Test
    void login_generatesTokenWithUserEmail() {
        LoginRequest request = buildLoginRequest();
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(buildUser()));
        when(jwtService.generateToken(any())).thenReturn("token");

        authService.login(request);

        verify(jwtService).generateToken(argThat(ud -> ud.getUsername().equals("john@example.com")));
    }
}
