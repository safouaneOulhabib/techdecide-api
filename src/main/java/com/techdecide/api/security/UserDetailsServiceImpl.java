package com.techdecide.api.security;

import com.techdecide.api.entity.TeamMembership;
import com.techdecide.api.repository.TeamMembershipRepository;
import com.techdecide.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final TeamMembershipRepository teamMembershipRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(user -> {
                    var membership = teamMembershipRepository.findByUserId(user.getId());
                    String teamRole = membership.map(TeamMembership::getTeamRole).orElse(null);
                    Long teamId = membership.map(m -> m.getTeam().getId()).orElse(null);
                    return new UserPrincipal(
                            user.getId(),
                            user.getEmail(),
                            user.getPassword(),
                            user.getAppRole(),
                            teamRole,
                            teamId
                    );
                })
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email
                ));
    }
}
