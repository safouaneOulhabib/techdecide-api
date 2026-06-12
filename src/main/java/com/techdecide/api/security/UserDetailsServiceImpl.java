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
                    String teamRole = teamMembershipRepository.findByUserId(user.getId())
                            .map(TeamMembership::getTeamRole)
                            .orElse(null);
                    return new UserPrincipal(
                            user.getId(),
                            user.getEmail(),
                            user.getPassword(),
                            user.getAppRole(),
                            teamRole
                    );
                })
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email
                ));
    }
}
