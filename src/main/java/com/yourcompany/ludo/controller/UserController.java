package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.BonusHistoryDto;
import com.yourcompany.ludo.dto.ProfileUpdateRequest;
import com.yourcompany.ludo.dto.UserDto;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.service.BonusHistoryService;
import com.yourcompany.ludo.service.NotificationService;
import com.yourcompany.ludo.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/user")
@Validated
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private static final String AVATAR_UPLOAD_DIR = System.getProperty("user.dir") + File.separator + "avatars" + File.separator;
    private static final long PROFILE_REQUEST_INTERVAL_MILLIS = 1000;

    @Autowired
    private UserService userService;

    @Autowired
    private BonusHistoryService bonusHistoryService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationService notificationService;

    private final Map<String, Instant> profileRequestTimestamps = new ConcurrentHashMap<>();

    // ================= Utility =================
    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");

        String gameId = authentication.getName(); // JWT subject is gameId
        return userService.findByGameId(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setMobile(user.getMobile());
        dto.setDisplayName(user.getDisplayName());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setBalance(user.getBalance());
        dto.setRole(user.getRole().name());
        dto.setGameId(user.getGameId());
        dto.setLifetimeEarnings(user.getLifetimeEarnings());
        dto.setReferralCode(user.getReferralCode());
        dto.setReferredBy(user.getReferredBy());
        dto.setSignupBonusClaimed(user.isSignupBonusClaimed());
        return dto;
    }

    private void broadcastUserUpdate(User user) {
        messagingTemplate.convertAndSend("/topic/user/" + user.getGameId(), convertToDto(user));
    }

    private static BigDecimal toMoney(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.DOWN);
    }

    // ==================== Endpoints ====================

    @GetMapping("/profile")
    public ResponseEntity<UserDto> getUserProfile(Authentication authentication) {
        return ResponseEntity.ok(convertToDto(getAuthenticatedUser(authentication)));
    }

    @GetMapping("/profile/{gameId}")
    public ResponseEntity<?> getUserProfileByGameId(@PathVariable String gameId) {
        Instant now = Instant.now();
        Instant lastRequest = profileRequestTimestamps.get(gameId);
        if (lastRequest != null && now.toEpochMilli() - lastRequest.toEpochMilli() < PROFILE_REQUEST_INTERVAL_MILLIS) {
            return ResponseEntity.status(429).body(Map.of("error", "Too many requests. Please slow down."));
        }
        profileRequestTimestamps.put(gameId, now);

        User user = userService.findByGameId(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(convertToDto(user));
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getBalance(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(Map.of("balance", user.getBalance()));
    }

    @GetMapping("/referral/{referralCode}")
    public ResponseEntity<?> getGameIdByReferralCode(@PathVariable String referralCode) {
        return userService.findGameIdByReferralCode(referralCode)
                .<ResponseEntity<?>>map(gameId -> ResponseEntity.ok(Map.of("gameId", gameId)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Invalid referral code")));
    }

    @PostMapping("/refer-bonus")
    public ResponseEntity<?> applyReferralBonus(@RequestParam @NotBlank String gameId) {
        User referrer = userService.findByGameId(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (referrer.isReferralBonusClaimed()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Referral bonus already claimed"));
        }

        userService.giveReferralBonus(referrer.getGameId(), referrer.getReferralCode());

        User updatedUser = userService.findByGameId(referrer.getGameId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        broadcastUserUpdate(updatedUser);
        notificationService.sendNotification(updatedUser.getId(), "Referral Bonus", "Referral bonus applied: " + updatedUser.getBalance());
        logger.info("Referral bonus applied to user {}: {}", updatedUser.getGameId(), updatedUser.getBalance());

        return ResponseEntity.ok(Map.of("message", "Referral bonus applied successfully", "balance", updatedUser.getBalance()));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody ProfileUpdateRequest request,
                                           Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        userService.updateProfile(user.getGameId(), request.getDisplayName(), request.getAvatarUrl());
        User updatedUser = userService.findByGameId(user.getGameId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        broadcastUserUpdate(updatedUser);
        notificationService.sendNotification(updatedUser.getId(), "Profile Updated", "Your profile has been successfully updated.");
        logger.info("Profile updated for user {}", updatedUser.getGameId());

        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file,
                                          Authentication authentication) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));

        try {
            User user = getAuthenticatedUser(authentication);
            File uploadDir = new File(AVATAR_UPLOAD_DIR);
            if (!uploadDir.exists()) uploadDir.mkdirs();

            String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
            if (ext == null) ext = "png";
            String filename = UUID.randomUUID() + "." + ext;
            File savedFile = new File(uploadDir, filename);
            file.transferTo(savedFile);

            String avatarUrl = "/avatars/" + filename;
            userService.updateAvatar(user.getGameId(), avatarUrl);

            User updatedUser = userService.findByGameId(user.getGameId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

            broadcastUserUpdate(updatedUser);
            notificationService.sendNotification(updatedUser.getId(), "Avatar Updated", "Your avatar has been successfully updated.");
            logger.info("Avatar uploaded for user {}", updatedUser.getGameId());

            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (IOException e) {
            logger.error("Failed to upload avatar", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to upload avatar"));
        }
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<?> removeAvatar(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        userService.updateAvatar(user.getGameId(), null);

        User updatedUser = userService.findByGameId(user.getGameId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        broadcastUserUpdate(updatedUser);
        notificationService.sendNotification(updatedUser.getId(), "Avatar Removed", "Your avatar has been removed");
        logger.info("Avatar removed for user {}", updatedUser.getGameId());

        return ResponseEntity.ok(Map.of("message", "Avatar removed successfully"));
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(Authentication authentication,
                                     @RequestParam @Min(0) double amount) {
        User user = getAuthenticatedUser(authentication);
        userService.addBalance(user.getGameId(), toMoney(amount));

        User updatedUser = userService.findByGameId(user.getGameId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        broadcastUserUpdate(updatedUser);
        notificationService.sendNotification(updatedUser.getId(), "Deposit", "Deposit successful: " + amount);
        return ResponseEntity.ok(Map.of("message", "Deposit successful", "balance", updatedUser.getBalance()));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(Authentication authentication,
                                     @RequestParam @Min(0) double amount) {
        User user = getAuthenticatedUser(authentication);
        BigDecimal withdrawAmount = toMoney(amount);

        if (user.getBalance().compareTo(withdrawAmount) < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Insufficient balance"));
        }

        userService.deductBalance(user.getGameId(), withdrawAmount);

        User updatedUser = userService.findByGameId(user.getGameId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        broadcastUserUpdate(updatedUser);
        notificationService.sendNotification(updatedUser.getId(), "Withdrawal", "Withdrawal successful: " + withdrawAmount);

        return ResponseEntity.ok(Map.of("message", "Withdraw successful", "balance", updatedUser.getBalance()));
    }

    @GetMapping("/bonus/history")
    public ResponseEntity<List<BonusHistoryDto>> getBonusHistory(@RequestParam(required = false) String gameId,
                                                                 Authentication authentication) {
        String uid = (gameId != null) ? gameId : getAuthenticatedUser(authentication).getGameId();

        List<BonusHistoryDto> history = bonusHistoryService.getUserBonusHistory(uid);

        return ResponseEntity.ok(history);
    }

}
