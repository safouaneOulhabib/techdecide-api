package com.techdecide.api.service;

import com.techdecide.api.dto.project.CreateProjectRequest;
import com.techdecide.api.dto.project.ProjectDTO;
import com.techdecide.api.dto.project.ProjectTeamDTO;
import com.techdecide.api.entity.Organization;
import com.techdecide.api.entity.Project;
import com.techdecide.api.entity.ProjectTeam;
import com.techdecide.api.entity.Team;
import com.techdecide.api.entity.User;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ForbiddenException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.OrganizationRepository;
import com.techdecide.api.repository.ProjectRepository;
import com.techdecide.api.repository.ProjectTeamRepository;
import com.techdecide.api.repository.TeamMembershipRepository;
import com.techdecide.api.repository.TeamRepository;
import com.techdecide.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final OrganizationRepository organizationRepository;
    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final UserRepository userRepository;

    public ProjectDTO create(CreateProjectRequest request, String actorEmail) {
        requireAppAdmin(actorEmail);
        Organization organization = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", request.getOrganizationId()));

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .organization(organization)
                .build();

        Project saved = projectRepository.saveAndFlush(project);
        return mapToDTO(saved, 0);
    }

    @Transactional(readOnly = true)
    public List<ProjectDTO> getAll(String actorEmail) {
        User actor = resolveUser(actorEmail);
        if ("APP_ADMIN".equals(actor.getAppRole())) {
            return projectRepository.findAll().stream()
                    .map(p -> {
                        int count = projectTeamRepository.findByProjectId(p.getId()).size();
                        return mapToDTO(p, count);
                    })
                    .collect(Collectors.toList());
        }

        Optional<com.techdecide.api.entity.TeamMembership> membership =
                teamMembershipRepository.findByUserId(actor.getId());
        if (membership.isEmpty()) {
            return List.of();
        }

        Long teamId = membership.get().getTeam().getId();
        return projectTeamRepository.findByTeamId(teamId).stream()
                .map(pt -> {
                    Project p = pt.getProject();
                    int count = projectTeamRepository.findByProjectId(p.getId()).size();
                    return mapToDTO(p, count);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectDTO getById(Long id, String actorEmail) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        User actor = resolveUser(actorEmail);

        if (!"APP_ADMIN".equals(actor.getAppRole())) {
            Optional<com.techdecide.api.entity.TeamMembership> membership =
                    teamMembershipRepository.findByUserId(actor.getId());
            if (membership.isEmpty()) {
                throw new ForbiddenException("Access denied");
            }
            Long teamId = membership.get().getTeam().getId();
            if (!projectTeamRepository.existsByProjectIdAndTeamId(id, teamId)) {
                throw new ForbiddenException("Access denied");
            }
        }

        int count = projectTeamRepository.findByProjectId(id).size();
        return mapToDTO(project, count);
    }

    public void delete(Long id, String actorEmail) {
        requireAppAdmin(actorEmail);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        projectRepository.delete(project);
    }

    public ProjectTeamDTO assignTeam(Long projectId, Long teamId, String actorEmail) {
        requireAppAdmin(actorEmail);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", teamId));

        if (projectTeamRepository.existsByProjectIdAndTeamId(projectId, teamId)) {
            throw new ConflictException("Team is already assigned to this project");
        }

        ProjectTeam projectTeam = ProjectTeam.builder()
                .project(project)
                .team(team)
                .build();

        projectTeamRepository.save(projectTeam);
        return ProjectTeamDTO.builder()
                .teamId(team.getId())
                .teamName(team.getName())
                .build();
    }

    public void removeTeam(Long projectId, Long teamId, String actorEmail) {
        requireAppAdmin(actorEmail);
        if (!projectRepository.findById(projectId).isPresent()) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        if (!teamRepository.findById(teamId).isPresent()) {
            throw new ResourceNotFoundException("Team", teamId);
        }
        if (!projectTeamRepository.existsByProjectIdAndTeamId(projectId, teamId)) {
            throw new ResourceNotFoundException("Team assignment not found");
        }
        projectTeamRepository.deleteByProjectIdAndTeamId(projectId, teamId);
    }

    @Transactional(readOnly = true)
    public List<ProjectTeamDTO> getTeams(Long projectId, String actorEmail) {
        if (!projectRepository.findById(projectId).isPresent()) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        return projectTeamRepository.findByProjectId(projectId).stream()
                .map(pt -> ProjectTeamDTO.builder()
                        .teamId(pt.getTeam().getId())
                        .teamName(pt.getTeam().getName())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProjectTeamDTO> getAvailableTeams(Long projectId, String actorEmail) {
        requireAppAdmin(actorEmail);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        Long orgId = project.getOrganization().getId();
        List<Team> orgTeams = teamRepository.findByOrganizationId(orgId);

        List<Long> assignedTeamIds = projectTeamRepository.findByProjectId(projectId).stream()
                .map(pt -> pt.getTeam().getId())
                .collect(Collectors.toList());

        return orgTeams.stream()
                .filter(t -> !assignedTeamIds.contains(t.getId()))
                .map(t -> ProjectTeamDTO.builder()
                        .teamId(t.getId())
                        .teamName(t.getName())
                        .build())
                .collect(Collectors.toList());
    }

    private void requireAppAdmin(String actorEmail) {
        User actor = resolveUser(actorEmail);
        if (!"APP_ADMIN".equals(actor.getAppRole())) {
            throw new ForbiddenException("Only APP_ADMIN can perform this action");
        }
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private ProjectDTO mapToDTO(Project project, int teamCount) {
        return ProjectDTO.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .organizationId(project.getOrganization().getId())
                .organizationName(project.getOrganization().getName())
                .teamCount(teamCount)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
