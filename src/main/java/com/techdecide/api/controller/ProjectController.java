package com.techdecide.api.controller;

import com.techdecide.api.dto.project.AssignTeamRequest;
import com.techdecide.api.dto.project.CreateProjectRequest;
import com.techdecide.api.dto.project.ProjectDTO;
import com.techdecide.api.dto.project.ProjectTeamDTO;
import com.techdecide.api.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ProjectDTO> create(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(projectService.create(request, userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<List<ProjectDTO>> getAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(projectService.getAll(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectDTO> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(projectService.getById(id, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        projectService.delete(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/teams")
    public ResponseEntity<List<ProjectTeamDTO>> getTeams(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(projectService.getTeams(id, userDetails.getUsername()));
    }

    @GetMapping("/{id}/available-teams")
    public ResponseEntity<List<ProjectTeamDTO>> getAvailableTeams(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(projectService.getAvailableTeams(id, userDetails.getUsername()));
    }

    @PostMapping("/{id}/teams")
    public ResponseEntity<ProjectTeamDTO> assignTeam(
            @PathVariable Long id,
            @Valid @RequestBody AssignTeamRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(projectService.assignTeam(id, request.getTeamId(), userDetails.getUsername()));
    }

    @DeleteMapping("/{id}/teams/{teamId}")
    public ResponseEntity<Void> removeTeam(
            @PathVariable Long id,
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserDetails userDetails) {
        projectService.removeTeam(id, teamId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
