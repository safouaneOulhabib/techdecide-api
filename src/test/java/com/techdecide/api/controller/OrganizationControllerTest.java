package com.techdecide.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdecide.api.dto.organization.CreateOrganizationRequest;
import com.techdecide.api.dto.organization.OrganizationDTO;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.security.JwtService;
import com.techdecide.api.security.TestSecurityConfig;
import com.techdecide.api.security.UserDetailsServiceImpl;
import com.techdecide.api.service.OrganizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrganizationController.class)
@Import(TestSecurityConfig.class)
class OrganizationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean OrganizationService organizationService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private OrganizationDTO buildDTO() {
        return OrganizationDTO.builder()
                .id(1L).name("Acme").description("Tech company")
                .createdAt(LocalDateTime.now()).build();
    }

    private CreateOrganizationRequest buildRequest() {
        CreateOrganizationRequest req = new CreateOrganizationRequest();
        req.setName("Acme");
        req.setDescription("Tech company");
        return req;
    }

    // --- POST /api/organizations ---

    @Test
    @WithMockUser
    void create_validRequest_returns201() throws Exception {
        when(organizationService.create(any())).thenReturn(buildDTO());

        mockMvc.perform(post("/api/organizations").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/organizations").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void create_missingName_returns400() throws Exception {
        CreateOrganizationRequest req = new CreateOrganizationRequest();
        req.setDescription("No name");

        mockMvc.perform(post("/api/organizations").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());

        verify(organizationService, never()).create(any());
    }

    @Test
    @WithMockUser
    void create_duplicateName_returns409() throws Exception {
        when(organizationService.create(any()))
                .thenThrow(new ConflictException("Organization with name 'Acme' already exists"));

        mockMvc.perform(post("/api/organizations").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Organization with name 'Acme' already exists"));
    }

    // --- GET /api/organizations ---

    @Test
    @WithMockUser
    void getAll_returns200WithList() throws Exception {
        when(organizationService.getAll()).thenReturn(List.of(buildDTO()));

        mockMvc.perform(get("/api/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Acme"));
    }

    @Test
    void getAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/organizations")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getAll_emptyList_returns200EmptyArray() throws Exception {
        when(organizationService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- GET /api/organizations/{id} ---

    @Test
    @WithMockUser
    void getById_existingId_returns200() throws Exception {
        when(organizationService.getById(1L)).thenReturn(buildDTO());

        mockMvc.perform(get("/api/organizations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    @Test
    @WithMockUser
    void getById_notFound_returns404() throws Exception {
        when(organizationService.getById(99L))
                .thenThrow(new ResourceNotFoundException("Organization", 99L));

        mockMvc.perform(get("/api/organizations/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Organization not found with id: 99"));
    }

    // --- PUT /api/organizations/{id} ---

    @Test
    @WithMockUser
    void update_validRequest_returns200() throws Exception {
        when(organizationService.update(eq(1L), any())).thenReturn(buildDTO());

        mockMvc.perform(put("/api/organizations/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    @Test
    @WithMockUser
    void update_notFound_returns404() throws Exception {
        when(organizationService.update(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Organization", 99L));

        mockMvc.perform(put("/api/organizations/99").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/organizations/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isUnauthorized());
    }

    // --- DELETE /api/organizations/{id} ---

    @Test
    @WithMockUser
    void delete_existingId_returns204() throws Exception {
        doNothing().when(organizationService).delete(1L);

        mockMvc.perform(delete("/api/organizations/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(organizationService).delete(1L);
    }

    @Test
    @WithMockUser
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Organization", 99L))
                .when(organizationService).delete(99L);

        mockMvc.perform(delete("/api/organizations/99").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/organizations/1").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
