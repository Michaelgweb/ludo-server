package com.yourcompany.ludo;

import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@SpringBootApplication
@EnableScheduling
public class LudoServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LudoServerApplication.class, args);
    }

    /**
     * Unique 12-digit Game ID generator
     */
    private String generateUniqueGameId(UserRepository userRepository) {
        Random random = new Random();
        String gameId;
        do {
            gameId = String.format("%012d", Math.abs(random.nextLong()) % 1_000_000_000_000L);
        } while (userRepository.findByGameId(gameId).isPresent());
        return gameId;
    }

    /**
     * Create Admin User after DB schema is ready
     */
    @Bean
    @Transactional
    CommandLineRunner createAdmin(UserRepository userRepository, PasswordEncoder encoder) {
        return args -> {
            String adminMobile = "01887869824";
            String adminPassword = "87869824";

            // Check if admin already exists by mobile
            Optional<User> optionalAdmin = userRepository.findByMobile(adminMobile);

            if (optionalAdmin.isEmpty()) {
                // Create new admin
                User admin = new User();
                admin.setMobile(adminMobile);
                admin.setPassword(encoder.encode(adminPassword));
                admin.setBalance(BigDecimal.ZERO);
                admin.setRole(User.Role.ADMIN);
                admin.setGameId(generateUniqueGameId(userRepository));

                // ✅ Set default referralCode (6 chars max)
                admin.setReferralCode(UUID.randomUUID().toString().replace("-", "").substring(0, 6));

                userRepository.save(admin);
                System.out.println("✅ Admin user created: " + adminMobile + " / " + adminPassword);
            } else {
                // Update existing admin if missing data
                User admin = optionalAdmin.get();
                boolean updated = false;

                if (admin.getGameId() == null || admin.getGameId().isBlank()) {
                    admin.setGameId(generateUniqueGameId(userRepository));
                    updated = true;
                }

                if (admin.getReferralCode() == null || admin.getReferralCode().isBlank()) {
                    // ✅ Set default referralCode (6 chars max)
                    admin.setReferralCode(UUID.randomUUID().toString().replace("-", "").substring(0, 6));
                    updated = true;
                }

                if (admin.getMobile() == null || admin.getMobile().isBlank()) {
                    admin.setMobile(adminMobile);
                    updated = true;
                }

                if (updated) {
                    userRepository.save(admin);
                    System.out.println("🔁 Admin user updated: gameId = " + admin.getGameId());
                } else {
                    System.out.println("ℹ️ Admin already exists: gameId = " + admin.getGameId());
                }
            }
        };
    }
}
