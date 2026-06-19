package com.flashsale.identityservice.service;

import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.identityservice.domain.model.User;
import com.flashsale.identityservice.domain.repository.UserRepository;
import com.flashsale.identityservice.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Custom UserDetailsService implementation for Spring Security
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        // Try to find by username first, then by email
        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username or email: " + usernameOrEmail));

        return buildUserDetails(user);
    }


    private UserDetailsImpl buildUserDetails(User user) {
        boolean enabled = "ACTIVE".equals(user.getStatus());

        // Fetch role from roles table using user ID
        String roleName = roleRepository.findFirstByUserIdOrderByIdAsc(user.getId())
                .map(role -> role.getRoleName())
                .orElse("BUYER"); // Default to BUYER if no role found

        return UserDetailsImpl.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .password(user.getPassword())
                .role(roleName)
                .enabled(enabled)
                .build();
    }
}

