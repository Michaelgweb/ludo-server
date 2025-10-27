package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.LoginRequest;
import com.yourcompany.ludo.dto.RegisterRequest;
import com.yourcompany.ludo.dto.UserDto;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.service.UserService;
import com.yourcompany.ludo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public User register(@RequestBody RegisterRequest request) {
        System.out.println("[AuthController] Register request for email: " + request.getEmail());
        User user = userService.register(request.getEmail(), request.getPassword());
        System.out.println("[AuthController] Registered user id: " + user.getId());
        return user;
    }

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest loginRequest) {
        System.out.println("[AuthController] Login attempt for email: " + loginRequest.getEmail());
        UserDto user = userService.login(loginRequest.getEmail(), loginRequest.getPassword());
        String token = jwtUtil.generateToken(user.getEmail());
        System.out.println("[AuthController] Login successful, token generated");
        return token;
    }
}
