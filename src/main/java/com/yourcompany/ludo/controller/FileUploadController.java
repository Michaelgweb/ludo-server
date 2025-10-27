package com.yourcompany.ludo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    @Value("${file.upload-dir}")
    private String uploadDir;

    // ওয়েবে ফাইল অ্যাক্সেসের জন্য URL পাথ (যেমন: /uploads/avatars)
    @Value("${file.upload-url-path:/uploads/avatars}")
    private String uploadUrlPath;

    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file,
                                          Authentication authentication,
                                          HttpServletRequest request) throws IOException {
        String gameId = authentication.getName();

        // ফাইল খালি কিনা চেক করা
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file provided");
        }

        // ফাইল এক্সটেনশন নেয়া
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext == null) {
            return ResponseEntity.badRequest().body("Invalid file extension");
        }

        // নাম তৈরি (gameId + random UUID)
        String filename = "avatar_" + gameId + "_" + UUID.randomUUID() + "." + ext;

        // আপলোড ফোল্ডার চেক ও তৈরি করা
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // ফাইল সেভ করা
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // URL বানানো - ওয়েব থেকে দেখা যাবে এমন URL
        String fileUrl = request.getScheme() + "://" +
                         request.getServerName() + ":" +
                         request.getServerPort() +
                         uploadUrlPath + "/" + filename;

        // রেসপন্স তৈরী
        Map<String, Object> response = new HashMap<>();
        response.put("url", fileUrl);
        response.put("message", "Avatar uploaded");

        return ResponseEntity.ok(response);
    }
}
