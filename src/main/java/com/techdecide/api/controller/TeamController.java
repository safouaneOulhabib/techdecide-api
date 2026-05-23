package com.techdecide.api.controller;

import com.techdecide.api.dto.team.CreateTeamRequest;
import com.techdecide.api.dto.team.TeamDTO;
import com.techdecide.api.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public ResponseEntity<TeamDTO> create(
            @Valid @RequestBody CreateTeamRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(teamService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<TeamDTO>> getAll() {
        return ResponseEntity.ok(teamService.getAll());
    }

    @GetMapping("/organization/{organizationId}")
    public ResponseEntity<List<TeamDTO>> getByOrganization(
            @PathVariable Long organizationId) {
        return ResponseEntity.ok(teamService.getByOrganization(organizationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeamDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateTeamRequest request) {
        return ResponseEntity.ok(teamService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        teamService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
