package com.techdecide.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdecide.api.dto.auth.AuthResponse;
import com.techdecide.api.dto.auth.LoginRequest;
import com.techdecide.api.dto.auth.RegisterRequest;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.security.JwtService;
import com.techdecide.api.security.TestSecurityConfig;
import com.techdecide.api.security.UserDetailsServiceImpl;
import com.techdecide.api.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
class AuthControllerTest {

    // QA matrix coverage:
    // AUTH-01 AUTH-02 AUTH-03 AUTH-04 AUTH-05 AUTH-06 AUTH-07

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private AuthResponse buildAuthResponse() {
        return AuthResponse.builder()
                .id(1L).token("jwt-token").email("john@example.com")
                .name("John Doe").appRole("USER").teamRole(null).build();
    }

    // --- /api/auth/register ---

    @Test
    void register_validRequest_returns200WithToken() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("John Doe");
        req.setEmail("john@example.com");
        req.setPassword("password123");
        when(authService.register(any())).thenReturn(buildAuthResponse());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.appRole").value("USER"));
    }

    @Test
    void register_missingName_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("john@example.com");
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());

        verify(authService, never()).register(any());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("John");
        req.setEmail("not-an-email");
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists());
    }

    @Test
    void register_passwordTooShort_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("John");
        req.setEmail("john@example.com");
        req.setPassword("short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").exists());
    }

    @Test
    void register_missingEmail_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("John");
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("John");
        req.setEmail("john@example.com");
        req.setPassword("password123");
        when(authService.register(any())).thenThrow(new ConflictException("Email already exists"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    // --- /api/auth/login ---

    @Test
    void login_validRequest_returns200WithToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("john@example.com");
        req.setPassword("password123");
        when(authService.login(any())).thenReturn(buildAuthResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    void login_missingEmail_returns400() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_invalidEmail_returns400() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("not-an-email");
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists());
    }

    @Test
    void login_missingPassword_returns400() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("john@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("john@example.com");
        req.setPassword("WrongPassword!");

        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }
}
