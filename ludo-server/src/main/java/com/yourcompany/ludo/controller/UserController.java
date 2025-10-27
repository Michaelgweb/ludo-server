package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.UserDto;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/me")
    public UserDto getUserProfile(Authentication authentication) {
        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        User user = optionalUser.orElseThrow(() -> new RuntimeException("User not found"));

        // DTO রিটার্ন করছি: id + email
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        return dto;
    }
}
