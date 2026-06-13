package com.techdecide.api.service;

import com.techdecide.api.dto.team.CreateTeamRequest;
import com.techdecide.api.dto.team.TeamDTO;
import com.techdecide.api.dto.team.UpdateTeamRequest;
import com.techdecide.api.entity.Organization;
import com.techdecide.api.entity.Team;
import com.techdecide.api.entity.User;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.OrganizationRepository;
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
public class TeamService {

    private final TeamRepository teamRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final TeamMembershipRepository teamMembershipRepository;

    public TeamDTO create(CreateTeamRequest request) {
        Organization organization = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", request.getOrganizationId()));

        if (teamRepository.existsByNameAndOrganizationId(request.getName(), request.getOrganizationId())) {
            throw new ConflictException("Team with name '" + request.getName() + "' already exists in this organization");
        }

        Team team = Team.builder()
                .name(request.getName())
                .organization(organization)
                .build();

        Team saved = teamRepository.save(team);
        return mapToDTO(saved);
    }

    public List<TeamDTO> getAll(String actorEmail) {
        User actor = userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if ("APP_ADMIN".equals(actor.getAppRole())) {
            return teamRepository.findAll()
                    .stream()
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());
        }

        Long teamId = teamMembershipRepository.findByUserId(actor.getId())
                .map(tm -> tm.getTeam().getId())
                .orElse(null);

        if (teamId == null) {
            return List.of();
        }

        return teamRepository.findById(teamId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<TeamDTO> getByOrganization(Long organizationId) {
        return teamRepository.findByOrganizationId(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public TeamDTO getById(Long id) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team", id));
        return mapToDTO(team);
    }

    public TeamDTO update(Long id, UpdateTeamRequest request) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team", id));

        if (request.getOrganizationId() != null) {
            Organization organization = organizationRepository.findById(request.getOrganizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", request.getOrganizationId()));
            team.setOrganization(organization);
        }

        String targetName = request.getName() != null ? request.getName() : team.getName();
        Long targetOrgId = team.getOrganization().getId();

        boolean nameChanged = !team.getName().equals(targetName);
        boolean orgChanged = request.getOrganizationId() != null
                && !team.getOrganization().getId().equals(request.getOrganizationId());

        if ((nameChanged || orgChanged)
                && teamRepository.existsByNameAndOrganizationId(targetName, targetOrgId)) {
            throw new ConflictException("Team with name '" + targetName + "' already exists in this organization");
        }

        if (request.getName() != null) team.setName(request.getName());

        Team updated = teamRepository.save(team);
        return mapToDTO(updated);
    }

    public void delete(Long id) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team", id));
        teamRepository.delete(team);
    }

    private TeamDTO mapToDTO(Team team) {
        return TeamDTO.builder()
                .id(team.getId())
                .name(team.getName())
                .organizationId(team.getOrganization().getId())
                .organizationName(team.getOrganization().getName())
                .createdAt(team.getCreatedAt())
                .build();
    }
}
