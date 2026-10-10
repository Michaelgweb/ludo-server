package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.BonusHistoryDto;
import com.yourcompany.ludo.dto.ProfileUpdateRequest;
import com.yourcompany.ludo.dto.UserDto;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.service.BonusHistoryService;
import com.yourcompany.ludo.service.NotificationService;
import com.yourcompany.ludo.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/user")
@Validated
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private static final long PROFILE_REQUEST_INTERVAL_MILLIS = 1000;
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");

    /** WebConfig এর সাথে একই প্রপার্টি, তাই সেভ ও সার্ভ সবসময় একই ফোল্ডারে */
    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.upload-url-path:/avatars}")
    private String uploadUrlPath;

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
        dto.setRole(user.getRole().name());
        dto.setGameId(user.getGameId());
        dto.setReferralCode(user.getReferralCode());
        dto.setReferredBy(user.getReferredBy());
        dto.setSignupBonusClaimed(user.isSignupBonusClaimed());

        // ================= Wallet =================
        dto.setBalance(user.getBalance());
        dto.setTurnoverBalance(user.getTurnoverBalance());
        dto.setWithdrawableBalance(user.getWithdrawableBalance());
        dto.setLifetimeEarnings(user.getLifetimeEarnings());
        dto.setLifetimeWithdraw(user.getLifetimeWithdraw());
        return dto;
    }

    /** অন্য ইউজারের জন্য শুধু পাবলিক তথ্য */
    private Map<String, Object> toPublicProfile(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gameId", user.getGameId());
        m.put("displayName", user.getDisplayName());
        m.put("avatarUrl", user.getAvatarUrl());
        return m;
    }

    private void broadcastUserUpdate(User user) {
        messagingTemplate.convertAndSend("/topic/user/" + user.getGameId(), convertToDto(user));
    }

    private User reload(String gameId) {
        return userService.findByGameId(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    /** আপলোড ফোল্ডারের পুরো পাথ (না থাকলে তৈরি হয়) */
    private Path avatarDir() throws IOException {
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        return dir;
    }

    /** আগের অবতার ফাইল মুছে ফেলা (ডিস্ক ভরে যাওয়া ঠেকাতে) */
    private void deleteOldAvatarFile(String oldUrl) {
        try {
            String prefix = uploadUrlPath + "/";
            if (oldUrl == null || !oldUrl.startsWith(prefix)) return;
            // শুধু ফাইলের নাম নেওয়া হয়, যাতে "../" দিয়ে অন্য ফোল্ডারে যাওয়া না যায়
            String name = Paths.get(oldUrl.substring(prefix.length())).getFileName().toString();
            Files.deleteIfExists(avatarDir().resolve(name));
        } catch (Exception e) {
            logger.warn("Could not delete old avatar {}: {}", oldUrl, e.getMessage());
        }
    }

    // ==================== Endpoints ====================

    @GetMapping("/profile")
    public ResponseEntity<UserDto> getUserProfile(Authentication authentication) {
        return ResponseEntity.ok(convertToDto(getAuthenticatedUser(authentication)));
    }

    @GetMapping("/profile/{gameId}")
    public ResponseEntity<?> getUserProfileByGameId(@PathVariable String gameId,
                                                    Authentication authentication) {
        User me = getAuthenticatedUser(authentication);

        // রেট লিমিট: রিকোয়েস্টকারী প্রতি আলাদা
        String limitKey = me.getGameId() + ":" + gameId;
        Instant now = Instant.now();
        Instant lastRequest = profileRequestTimestamps.get(limitKey);
        if (lastRequest != null && now.toEpochMilli() - lastRequest.toEpochMilli() < PROFILE_REQUEST_INTERVAL_MILLIS) {
            return ResponseEntity.status(429).body(Map.of("error", "Too many requests. Please slow down."));
        }
        profileRequestTimestamps.put(limitKey, now);

        User target = reload(gameId);

        // নিজের প্রোফাইল হলে পুরো তথ্য, অন্যের হলে শুধু পাবলিক
        if (me.getGameId().equals(target.getGameId())) {
            return ResponseEntity.ok(convertToDto(target));
        }
        return ResponseEntity.ok(toPublicProfile(target));
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getBalance(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(Map.of(
                "balance", user.getBalance(),
                "turnoverBalance", user.getTurnoverBalance(),
                "withdrawableBalance", user.getWithdrawableBalance()));
    }

    @GetMapping("/referral/{referralCode}")
    public ResponseEntity<?> getGameIdByReferralCode(@PathVariable String referralCode) {
        return userService.findGameIdByReferralCode(referralCode)
                .<ResponseEntity<?>>map(gameId -> ResponseEntity.ok(Map.of("gameId", gameId)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Invalid referral code")));
    }

    // ✅ /refer-bonus সরানো হয়েছে: রেফারেল বোনাস রেজিস্ট্রেশনের সময়ই UserService থেকে রেকর্ড হয়

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody ProfileUpdateRequest request,
                                           Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        userService.updateProfile(user.getGameId(), request.getDisplayName(), request.getAvatarUrl());
        User updatedUser = reload(user.getGameId());

        broadcastUserUpdate(updatedUser);
        notificationService.sendNotification(updatedUser.getId(), "Profile Updated", "Your profile has been successfully updated.");
        logger.info("Profile updated for user {}", updatedUser.getGameId());

        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file,
                                          Authentication authentication) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));

        // শুধু ছবি: এক্সটেনশন ও কন্টেন্ট-টাইপ দুটোই যাচাই
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        ext = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        String contentType = file.getContentType();
        if (!ALLOWED_EXT.contains(ext) || contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only jpg, jpeg, png or webp images are allowed"));
        }

        try {
            User user = getAuthenticatedUser(authentication);
            String oldUrl = user.getAvatarUrl();

            Path dir = avatarDir();
            String filename = UUID.randomUUID() + "." + ext;
            file.transferTo(dir.resolve(filename));

            String avatarUrl = uploadUrlPath + "/" + filename;
            userService.updateAvatar(user.getGameId(), avatarUrl);

            // নতুন ছবি সেভ হওয়ার পরেই পুরনোটা মোছা হয়
            deleteOldAvatarFile(oldUrl);

            User updatedUser = reload(user.getGameId());

            broadcastUserUpdate(updatedUser);
            notificationService.sendNotification(updatedUser.getId(), "Avatar Updated", "Your avatar has been successfully updated.");
            logger.info("Avatar uploaded for user {} -> {}", updatedUser.getGameId(), avatarUrl);

            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (IOException e) {
            logger.error("Failed to upload avatar", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to upload avatar"));
        }
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<?> removeAvatar(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        String oldUrl = user.getAvatarUrl();
        userService.updateAvatar(user.getGameId(), null);
        deleteOldAvatarFile(oldUrl);

        User updatedUser = reload(user.getGameId());

        broadcastUserUpdate(updatedUser);
        notificationService.sendNotification(updatedUser.getId(), "Avatar Removed", "Your avatar has been removed");
        logger.info("Avatar removed for user {}", updatedUser.getGameId());

        return ResponseEntity.ok(Map.of("message", "Avatar removed successfully"));
    }

    /** শুধু নিজের বোনাস হিস্ট্রি; অন্যের gameId দিলে 403 */
    @GetMapping("/bonus/history")
    public ResponseEntity<List<BonusHistoryDto>> getBonusHistory(@RequestParam(required = false) String gameId,
                                                                 Authentication authentication) {
        User me = getAuthenticatedUser(authentication);
        if (gameId != null && !gameId.equals(me.getGameId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
        return ResponseEntity.ok(bonusHistoryService.getUserBonusHistory(me.getGameId()));
    }
}
