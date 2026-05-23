package com.techdecide.api.service;

import com.techdecide.api.dto.team.CreateTeamRequest;
import com.techdecide.api.dto.team.TeamDTO;
import com.techdecide.api.entity.Organization;
import com.techdecide.api.entity.Team;
import com.techdecide.api.exception.ConflictException;
import com.techdecide.api.exception.ResourceNotFoundException;
import com.techdecide.api.repository.OrganizationRepository;
import com.techdecide.api.repository.TeamRepository;
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

    public List<TeamDTO> getAll() {
        return teamRepository.findAll()
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

    public TeamDTO update(Long id, CreateTeamRequest request) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team", id));

        Organization organization = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", request.getOrganizationId()));

        boolean nameChanged = !team.getName().equals(request.getName());
        boolean orgChanged = !team.getOrganization().getId().equals(request.getOrganizationId());

        if ((nameChanged || orgChanged)
                && teamRepository.existsByNameAndOrganizationId(request.getName(), request.getOrganizationId())) {
            throw new ConflictException("Team with name '" + request.getName() + "' already exists in this organization");
        }

        if (request.getName() != null) team.setName(request.getName());
        team.setOrganization(organization);

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
