package com.techdecide.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdecide.api.dto.tag.CreateTagRequest;
import com.techdecide.api.dto.tag.TagDTO;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.security.JwtService;
import com.techdecide.api.security.TestSecurityConfig;
import com.techdecide.api.security.UserDetailsServiceImpl;
import com.techdecide.api.service.TagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import com.techdecide.api.exception.ForbiddenException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TagController.class)
@Import(TestSecurityConfig.class)
class TagControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean TagService tagService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private TagDTO buildDTO() {
        return TagDTO.builder().id(1L).name("backend").color("#ff0000").build();
    }

    private CreateTagRequest buildRequest() {
        CreateTagRequest req = new CreateTagRequest();
        req.setName("backend");
        req.setColor("#ff0000");
        return req;
    }

    // --- POST /api/tags ---

    @Test
    @WithMockUser
    void create_appAdmin_returns201() throws Exception {
        when(tagService.create(any(), anyString())).thenReturn(buildDTO());

        mockMvc.perform(post("/api/tags").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("backend"))
                .andExpect(jsonPath("$.color").value("#ff0000"));
    }

    @Test
    @WithMockUser
    void create_nonAppAdmin_returns403() throws Exception {
        when(tagService.create(any(), anyString()))
                .thenThrow(new ForbiddenException("Only APP_ADMIN can manage tags"));

        mockMvc.perform(post("/api/tags").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/tags").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void create_missingName_returns400() throws Exception {
        CreateTagRequest req = new CreateTagRequest();
        req.setColor("#ff0000");

        mockMvc.perform(post("/api/tags").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());

        verify(tagService, never()).create(any(), any());
    }

    @Test
    @WithMockUser
    void create_blankName_returns400() throws Exception {
        CreateTagRequest req = new CreateTagRequest();
        req.setName("  ");

        mockMvc.perform(post("/api/tags").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void create_duplicateName_returns409() throws Exception {
        when(tagService.create(any(), anyString()))
                .thenThrow(new ConflictException("Tag with name 'backend' already exists"));

        mockMvc.perform(post("/api/tags").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tag with name 'backend' already exists"));
    }

    // --- GET /api/tags ---

    @Test
    @WithMockUser
    void getAll_returns200WithList() throws Exception {
        when(tagService.getAll()).thenReturn(List.of(buildDTO()));

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("backend"));
    }

    @Test
    void getAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/tags")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getAll_emptyList_returns200EmptyArray() throws Exception {
        when(tagService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- GET /api/tags/{id} ---

    @Test
    @WithMockUser
    void getById_existingId_returns200() throws Exception {
        when(tagService.getById(1L)).thenReturn(buildDTO());

        mockMvc.perform(get("/api/tags/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("backend"));
    }

    @Test
    @WithMockUser
    void getById_notFound_returns404() throws Exception {
        when(tagService.getById(99L)).thenThrow(new ResourceNotFoundException("Tag", 99L));

        mockMvc.perform(get("/api/tags/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Tag not found with id: 99"));
    }

    // --- DELETE /api/tags/{id} ---

    @Test
    @WithMockUser
    void delete_appAdmin_returns204() throws Exception {
        doNothing().when(tagService).delete(eq(1L), anyString());

        mockMvc.perform(delete("/api/tags/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(tagService).delete(eq(1L), anyString());
    }

    @Test
    @WithMockUser
    void delete_nonAppAdmin_returns403() throws Exception {
        doThrow(new ForbiddenException("Only APP_ADMIN can manage tags"))
                .when(tagService).delete(eq(1L), anyString());

        mockMvc.perform(delete("/api/tags/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Tag", 99L)).when(tagService).delete(eq(99L), anyString());

        mockMvc.perform(delete("/api/tags/99").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/tags/1").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
