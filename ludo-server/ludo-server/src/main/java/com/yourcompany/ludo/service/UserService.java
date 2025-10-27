package com.yourcompany.ludo.service;

import com.yourcompany.ludo.dto.UserDto;
import com.yourcompany.ludo.model.User;

public interface UserService {
    User register(String email, String password);
    UserDto login(String email, String password);

}
