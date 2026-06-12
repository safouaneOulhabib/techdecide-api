package com.techdecide.api.controller;

import com.techdecide.api.dto.team.AssignMemberRequest;
import com.techdecide.api.dto.team.ChangeRoleRequest;
import com.techdecide.api.dto.team.TeamMemberDTO;
import com.techdecide.api.service.TeamMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams/{teamId}/members")
@RequiredArgsConstructor
public class TeamMemberController {

    private final TeamMemberService teamMemberService;

    @GetMapping
    public ResponseEntity<List<TeamMemberDTO>> getMembers(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(teamMemberService.getMembers(teamId, userDetails.getUsername()));
    }

    @PostMapping
    public ResponseEntity<TeamMemberDTO> assignMember(
            @PathVariable Long teamId,
            @Valid @RequestBody AssignMemberRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamMemberService.assignMember(teamId, request, userDetails.getUsername()));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long teamId,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        teamMemberService.removeMember(teamId, userId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<TeamMemberDTO> changeRole(
            @PathVariable Long teamId,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeRoleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(teamMemberService.changeRole(teamId, userId, request, userDetails.getUsername()));
    }
}
