package com.techdecide.api.service;

import com.techdecide.api.dto.team.AssignMemberRequest;
import com.techdecide.api.dto.team.ChangeRoleRequest;
import com.techdecide.api.dto.team.TeamMemberDTO;
import com.techdecide.api.entity.Team;
import com.techdecide.api.entity.User;
import com.techdecide.api.exception.BadRequestException;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.TeamRepository;
import com.techdecide.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TeamMemberService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    public List<TeamMemberDTO> getMembers(Long teamId, String currentUserEmail) {
        User currentUser = resolveUser(currentUserEmail);
        requireAdminOrTechLead(currentUser);
        requireTeamExists(teamId);
        return userRepository.findAllByTeamId(teamId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public TeamMemberDTO assignMember(Long teamId, AssignMemberRequest request, String currentUserEmail) {
        User currentUser = resolveUser(currentUserEmail);
        requireAdminOrTechLead(currentUser);
        Team team = requireTeamExists(teamId);
        User target = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));
        target.setTeam(team);
        User saved = userRepository.save(target);
        return mapToDTO(saved);
    }

    public void removeMember(Long teamId, Long userId, String currentUserEmail) {
        User currentUser = resolveUser(currentUserEmail);
        requireAdminOrTechLead(currentUser);
        if (currentUser.getId().equals(userId)) {
            throw new BadRequestException("Cannot remove yourself from a team");
        }
        requireTeamExists(teamId);
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        requireUserInTeam(target, teamId);
        target.setTeam(null);
        userRepository.save(target);
    }

    public TeamMemberDTO changeRole(Long teamId, Long userId, ChangeRoleRequest request, String currentUserEmail) {
        User currentUser = resolveUser(currentUserEmail);
        requireAdmin(currentUser);
        if (currentUser.getId().equals(userId)) {
            throw new BadRequestException("Cannot change your own role");
        }
        requireTeamExists(teamId);
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        requireUserInTeam(target, teamId);
        User.Role newRole = parseRole(request.getRole());
        target.setRole(newRole);
        User saved = userRepository.save(target);
        return mapToDTO(saved);
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Team requireTeamExists(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", teamId));
    }

    private void requireAdminOrTechLead(User user) {
        if (user.getRole() != User.Role.ADMIN && user.getRole() != User.Role.TECH_LEAD) {
            throw new ForbiddenException("Only ADMIN or TECH_LEAD can manage team members");
        }
    }

    private void requireAdmin(User user) {
        if (user.getRole() != User.Role.ADMIN) {
            throw new ForbiddenException("Only ADMIN can change a user's role");
        }
    }

    private void requireUserInTeam(User user, Long teamId) {
        if (user.getTeam() == null || !user.getTeam().getId().equals(teamId)) {
            throw new BadRequestException("User is not a member of this team");
        }
    }

    private User.Role parseRole(String role) {
        try {
            return User.Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + role);
        }
    }

    private TeamMemberDTO mapToDTO(User user) {
        return TeamMemberDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .teamId(user.getTeam() != null ? user.getTeam().getId() : null)
                .build();
    }
}
