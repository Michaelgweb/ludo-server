package com.yourcompany.ludo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
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

    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file,
                                          Authentication authentication,
                                          HttpServletRequest request) throws IOException {
        String gameId = authentication.getName();

        // Check empty
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file provided");
        }

        // File extension
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String filename = "avatar_" + gameId + "_" + UUID.randomUUID() + "." + ext;

        // Path setup
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Serve URL
        String fileUrl = request.getScheme() + "://" +
                         request.getServerName() + ":" +
                         request.getServerPort() + "/" +
                         uploadDir + "/" + filename;

        Map<String, Object> response = new HashMap<>();
        response.put("url", fileUrl);
        response.put("message", "Avatar uploaded");

        return ResponseEntity.ok(response);
    }
}
