
// === UserRepository.java ===
package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}

