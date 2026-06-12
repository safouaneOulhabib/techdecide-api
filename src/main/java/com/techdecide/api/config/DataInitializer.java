package com.techdecide.api.config;

import com.techdecide.api.entity.Organization;
import com.techdecide.api.entity.Team;
import com.techdecide.api.entity.TeamMembership;
import com.techdecide.api.entity.User;
import com.techdecide.api.repository.OrganizationRepository;
import com.techdecide.api.repository.TeamMembershipRepository;
import com.techdecide.api.repository.TeamRepository;
import com.techdecide.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String DEFAULT_PASSWORD = "Test1234!";

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            TeamRepository teamRepository,
            TeamMembershipRepository teamMembershipRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.teamRepository = teamRepository;
        this.teamMembershipRepository = teamMembershipRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== DataInitializer: seeding dev data ===");

        Organization org = seedOrganization("TechDecide Corp");
        Team backendTeam = seedTeam("Backend Team", org);
        Team devopsTeam = seedTeam("Devops Team", org);

        User backendLead  = seedUser("teamadmin.backend@techdecide.com", "Backend Lead",  "USER");
        User backendMember = seedUser("member.backend@techdecide.com",   "Backend Member", "USER");
        User devopsLead   = seedUser("teamadmin.devops@techdecide.com",  "Devops Lead",   "USER");
        User devopsMember = seedUser("member.devops@techdecide.com",     "Devops Member", "USER");
        seedUser("noTeam@techdecide.com",    "No Team User", "USER");
        seedUser("admin@techdecide.com",     "App Admin",    "APP_ADMIN");

        seedMembership(backendLead,   backendTeam, "TEAM_ADMIN");
        seedMembership(backendMember, backendTeam, "MEMBER");
        seedMembership(devopsLead,    devopsTeam,  "TEAM_ADMIN");
        seedMembership(devopsMember,  devopsTeam,  "MEMBER");

        log.info("=== DataInitializer: done ===");
    }

    private Organization seedOrganization(String name) {
        if (organizationRepository.existsByName(name)) {
            log.info("Organization '{}' already exists, skipping", name);
            return organizationRepository.findByName(name).orElseThrow();
        }
        Organization org = organizationRepository.save(Organization.builder().name(name).build());
        log.info("Created organization '{}'", org.getName());
        return org;
    }

    private Team seedTeam(String name, Organization org) {
        if (teamRepository.existsByNameAndOrganizationId(name, org.getId())) {
            log.info("Team '{}' already exists, skipping", name);
            return teamRepository.findByNameAndOrganizationId(name, org.getId()).orElseThrow();
        }
        Team team = teamRepository.save(Team.builder().name(name).organization(org).build());
        log.info("Created team '{}'", team.getName());
        return team;
    }

    private User seedUser(String email, String name, String appRole) {
        if (userRepository.existsByEmail(email)) {
            log.info("User '{}' already exists, skipping", email);
            return userRepository.findByEmail(email).orElseThrow();
        }
        User user = userRepository.save(User.builder()
                .email(email)
                .name(name)
                .appRole(appRole)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .build());
        log.info("Created user '{}' ({})", email, appRole);
        return user;
    }

    private void seedMembership(User user, Team team, String teamRole) {
        if (teamMembershipRepository.existsByUserIdAndTeamId(user.getId(), team.getId())) {
            log.info("Membership {}/{} already exists, skipping", user.getEmail(), team.getName());
            return;
        }
        teamMembershipRepository.save(TeamMembership.builder()
                .user(user)
                .team(team)
                .teamRole(teamRole)
                .build());
        log.info("Created membership: {} -> {} ({})", user.getEmail(), team.getName(), teamRole);
    }
}
