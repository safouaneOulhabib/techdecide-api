package com.techdecide.api.service;

import com.techdecide.api.dto.team.AssignMemberRequest;
import com.techdecide.api.dto.team.AvailableUserDTO;
import com.techdecide.api.dto.team.ChangeRoleRequest;
import com.techdecide.api.dto.team.TeamMemberDTO;
import com.techdecide.api.entity.Team;
import com.techdecide.api.entity.TeamMembership;
import com.techdecide.api.entity.User;
import com.techdecide.api.exception.BadRequestException;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.TeamMembershipRepository;
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
    private final TeamMembershipRepository teamMembershipRepository;

    @Transactional(readOnly = true)
    public List<AvailableUserDTO> getAvailableUsers(Long teamId, String currentUserEmail) {
        User currentUser = resolveUser(currentUserEmail);
        requireAdminOrTeamAdmin(currentUser, teamId);
        requireTeamExists(teamId);
        return userRepository.findUsersWithNoTeamMembership()
                .stream()
                .map(u -> AvailableUserDTO.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .email(u.getEmail())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TeamMemberDTO> getMembers(Long teamId, String currentUserEmail) {
        User currentUser = resolveUser(currentUserEmail);
        requireAdminOrTeamAdmin(currentUser, teamId);
        requireTeamExists(teamId);
        return teamMembershipRepository.findByTeamId(teamId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public TeamMemberDTO assignMember(Long teamId, AssignMemberRequest request, String currentUserEmail) {
        User currentUser = resolveUser(currentUserEmail);
        requireAdminOrTeamAdmin(currentUser, teamId);
        Team team = requireTeamExists(teamId);
        User target = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));
        if (teamMembershipRepository.existsByUserIdAndTeamId(target.getId(), teamId)) {
            throw new BadRequestException("User is already a member of this team");
        }
        TeamMembership membership = TeamMembership.builder()
                .user(target)
                .team(team)
                .teamRole("MEMBER")
                .build();
        TeamMembership saved = teamMembershipRepository.save(membership);
        return mapToDTO(saved);
    }

    public void removeMember(Long teamId, Long userId, String currentUserEmail) {
        User currentUser = resolveUser(currentUserEmail);
        requireAdminOrTeamAdmin(currentUser, teamId);
        if (currentUser.getId().equals(userId)) {
            throw new BadRequestException("Cannot remove yourself from a team");
        }
        requireTeamExists(teamId);
        TeamMembership membership = teamMembershipRepository.findByUserIdAndTeamId(userId, teamId)
                .orElseThrow(() -> new BadRequestException("User is not a member of this team"));
        teamMembershipRepository.delete(membership);
    }

    public TeamMemberDTO changeRole(Long teamId, Long userId, ChangeRoleRequest request, String currentUserEmail) {
        User currentUser = resolveUser(currentUserEmail);
        requireAdminOrTeamAdmin(currentUser, teamId);
        if (currentUser.getId().equals(userId)) {
            throw new BadRequestException("Cannot change your own role");
        }
        requireTeamExists(teamId);
        TeamMembership membership = teamMembershipRepository.findByUserIdAndTeamId(userId, teamId)
                .orElseThrow(() -> new BadRequestException("User is not a member of this team"));
        String newTeamRole = parseTeamRole(request.getRole());
        membership.setTeamRole(newTeamRole);
        TeamMembership saved = teamMembershipRepository.save(membership);
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

    private void requireAdminOrTeamAdmin(User user, Long teamId) {
        if ("APP_ADMIN".equals(user.getAppRole())) {
            return;
        }
        teamMembershipRepository.findByUserIdAndTeamId(user.getId(), teamId)
                .filter(m -> "TEAM_ADMIN".equals(m.getTeamRole()))
                .orElseThrow(() -> new ForbiddenException("Only APP_ADMIN or TEAM_ADMIN can manage team members"));
    }

    private String parseTeamRole(String role) {
        if ("TEAM_ADMIN".equals(role) || "MEMBER".equals(role)) {
            return role;
        }
        throw new BadRequestException("Invalid team role: " + role + ". Must be TEAM_ADMIN or MEMBER");
    }

    private TeamMemberDTO mapToDTO(TeamMembership membership) {
        return TeamMemberDTO.builder()
                .userId(membership.getUser().getId())
                .name(membership.getUser().getName())
                .email(membership.getUser().getEmail())
                .teamRole(membership.getTeamRole())
                .teamId(membership.getTeam().getId())
                .build();
    }
}
