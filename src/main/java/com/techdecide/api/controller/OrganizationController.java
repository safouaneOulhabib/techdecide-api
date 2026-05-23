package com.techdecide.api.controller;

import com.techdecide.api.dto.organization.CreateOrganizationRequest;
import com.techdecide.api.dto.organization.OrganizationDTO;
import com.techdecide.api.dto.organization.UpdateOrganizationRequest;
import com.techdecide.api.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<OrganizationDTO> create(
            @Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(organizationService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<OrganizationDTO>> getAll() {
        return ResponseEntity.ok(organizationService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrganizationDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(organizationService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrganizationDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        return ResponseEntity.ok(organizationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        organizationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
