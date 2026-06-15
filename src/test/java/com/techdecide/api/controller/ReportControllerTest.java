package com.techdecide.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdecide.api.dto.report.*;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.security.JwtService;
import com.techdecide.api.security.TestSecurityConfig;
import com.techdecide.api.security.UserDetailsServiceImpl;
import com.techdecide.api.service.ReportService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportController.class)
@Import(TestSecurityConfig.class)
class ReportControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ReportService reportService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private ReportDTO buildReportDTO() {
        ReportItemDTO item = ReportItemDTO.builder()
                .id(1L).originalDecisionId(10L)
                .decisionTitle("Use PostgreSQL").decisionStatus("APPROVED")
                .decisionContext("We need a DB").decisionContent("PostgreSQL chosen")
                .decisionConsequences("Cost implications")
                .decisionTeamName("GTN").decisionAuthorName("Alice")
                .decisionCreatedAt(LocalDateTime.now())
                .alternatives(List.of())
                .position(0)
                .build();

        return ReportDTO.builder()
                .id(1L).title("My Report").introduction("Intro")
                .authorId(1L).authorName("Alice")
                .projectId(1L).projectName("GTN")
                .createdAt(LocalDateTime.now())
                .items(List.of(item))
                .build();
    }

    private ReportSummaryDTO buildSummaryDTO() {
        return ReportSummaryDTO.builder()
                .id(1L).title("My Report").authorId(1L).authorName("Alice")
                .projectId(1L).projectName("GTN")
                .createdAt(LocalDateTime.now()).itemCount(1)
                .build();
    }

    private CreateReportRequest buildCreateRequest() {
        CreateReportRequest req = new CreateReportRequest();
        req.setProjectId(1L);
        req.setTitle("My Report");
        req.setIntroduction("Intro");
        req.setDecisionIds(List.of(10L));
        return req;
    }

    // --- POST /api/reports ---

    @Test
    @WithMockUser
    void create_validRequest_returns201() throws Exception {
        when(reportService.create(any(), anyString())).thenReturn(buildReportDTO());

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("My Report"))
                .andExpect(jsonPath("$.authorId").value(1))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].decisionTitle").value("Use PostgreSQL"));
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void create_missingTitle_returns400() throws Exception {
        CreateReportRequest req = buildCreateRequest();
        req.setTitle(null);

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(reportService, never()).create(any(), any());
    }

    // --- GET /api/reports ---

    @Test
    @WithMockUser
    void getAll_authenticated_returns200WithList() throws Exception {
        when(reportService.getAll(anyString())).thenReturn(List.of(buildSummaryDTO()));

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].title").value("My Report"))
                .andExpect(jsonPath("$[0].authorId").value(1))
                .andExpect(jsonPath("$[0].itemCount").value(1));
    }

    @Test
    void getAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/reports")).andExpect(status().isUnauthorized());
    }

    // --- GET /api/reports/{id} ---

    @Test
    @WithMockUser
    void getById_existingId_returns200WithFullDTO() throws Exception {
        when(reportService.getById(eq(1L), anyString())).thenReturn(buildReportDTO());

        mockMvc.perform(get("/api/reports/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.authorId").value(1))
                .andExpect(jsonPath("$.items[0].decisionStatus").value("APPROVED"));
    }

    @Test
    @WithMockUser
    void getById_notFound_returns404() throws Exception {
        when(reportService.getById(eq(99L), anyString()))
                .thenThrow(new ResourceNotFoundException("Report", 99L));

        mockMvc.perform(get("/api/reports/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Report not found with id: 99"));
    }

    // --- PUT /api/reports/{id} ---

    @Test
    @WithMockUser
    void update_byAuthor_returns200() throws Exception {
        UpdateReportRequest req = new UpdateReportRequest();
        req.setTitle("Updated Title");
        ReportDTO updated = buildReportDTO();
        updated.setTitle("Updated Title");
        when(reportService.update(eq(1L), any(), anyString())).thenReturn(updated);

        mockMvc.perform(put("/api/reports/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    @WithMockUser
    void update_byNonAuthor_returns403() throws Exception {
        when(reportService.update(eq(1L), any(), anyString()))
                .thenThrow(new ForbiddenException("Only the author can edit this report"));

        mockMvc.perform(put("/api/reports/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateReportRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only the author can edit this report"));
    }

    @Test
    void update_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/reports/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateReportRequest())))
                .andExpect(status().isUnauthorized());
    }

    // --- DELETE /api/reports/{id} ---

    @Test
    @WithMockUser
    void delete_byAuthor_returns204() throws Exception {
        doNothing().when(reportService).delete(eq(1L), anyString());

        mockMvc.perform(delete("/api/reports/1"))
                .andExpect(status().isNoContent());

        verify(reportService).delete(eq(1L), anyString());
    }

    @Test
    @WithMockUser
    void delete_byNonAuthor_returns403() throws Exception {
        doThrow(new ForbiddenException("Only the author can delete this report"))
                .when(reportService).delete(eq(1L), anyString());

        mockMvc.perform(delete("/api/reports/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/reports/1"))
                .andExpect(status().isUnauthorized());
    }
}
