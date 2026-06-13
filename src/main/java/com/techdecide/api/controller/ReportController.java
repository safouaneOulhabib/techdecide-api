package com.techdecide.api.controller;

import com.techdecide.api.dto.report.*;
import com.techdecide.api.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ReportDTO> create(
            @Valid @RequestBody CreateReportRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(reportService.create(request, userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<List<ReportSummaryDTO>> getAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(reportService.getAll(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportDTO> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(reportService.getById(id, userDetails.getUsername()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReportDTO> update(
            @PathVariable Long id,
            @RequestBody UpdateReportRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(reportService.update(id, request, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        reportService.delete(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
