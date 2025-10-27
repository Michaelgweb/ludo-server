package com.yourcompany.ludo.service.impl;

import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String gameId) throws UsernameNotFoundException {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with Game ID: " + gameId));

        // Enum থেকে String রূপান্তর
        String roleName = user.getRole().name(); // "USER" বা "ADMIN"

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getGameId())
                .password(user.getPassword())
                .authorities("ROLE_" + roleName) // Spring Security convention
                .build();
    }
}
