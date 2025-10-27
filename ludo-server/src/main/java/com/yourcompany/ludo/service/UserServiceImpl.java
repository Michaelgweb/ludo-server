package com.yourcompany.ludo.service;

import com.yourcompany.ludo.dto.UserDto;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService, UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public User register(String email, String password) {
        System.out.println("[UserService] Registering user: " + email);
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        User savedUser = userRepository.save(user);
        System.out.println("[UserService] User registered with id: " + savedUser.getId());
        return savedUser;
    }

    @Override
    public UserDto login(String email, String password) {
        System.out.println("[UserService] Login called for email: " + email);
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> {
                System.out.println("[UserService] User not found: " + email);
                return new RuntimeException("User not found");
            });

        if (!passwordEncoder.matches(password, user.getPassword())) {
            System.out.println("[UserService] Invalid password for user: " + email);
            throw new RuntimeException("Invalid password");
        }

        System.out.println("[UserService] User logged in successfully: " + email);

        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        return dto;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        System.out.println("[UserService] loadUserByUsername called with email: " + email);
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> {
                System.out.println("[UserService] User not found with email: " + email);
                return new UsernameNotFoundException("User not found with email: " + email);
            });

        System.out.println("[UserService] Returning UserDetails for email: " + email);
        return org.springframework.security.core.userdetails.User
            .withUsername(user.getEmail())
            .password(user.getPassword())
            .authorities("USER")
            .build();
    }
}
