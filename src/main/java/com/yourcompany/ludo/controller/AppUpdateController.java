package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.model.AppVersion;
import com.yourcompany.ludo.repository.AppVersionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/api")
public class AppUpdateController {

    private final AppVersionRepository repo;

    @Value("${app.apk.dir:apk}")
    private String apkDir;

    @Value("${app.public-url}")
    private String publicUrl;

    public AppUpdateController(AppVersionRepository repo) {
        this.repo = repo;
    }

    // সবাই দেখতে পারবে
    @GetMapping("/version")
    public ResponseEntity<AppVersion> latest() {
        return repo.findTopByOrderByVersionCodeDesc()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // শুধু অ্যাডমিন
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/admin/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("apk") MultipartFile apk,
                                    @RequestParam("versionName") String versionName,
                                    @RequestParam("versionCode") int versionCode,
                                    @RequestParam(value = "force", defaultValue = "false") boolean force)
            throws IOException {

        String orig = apk.getOriginalFilename();
        if (orig == null || !orig.toLowerCase().endsWith(".apk")) {
            return ResponseEntity.badRequest().body("Only .apk allowed");
        }

        int latest = repo.findTopByOrderByVersionCodeDesc()
                .map(AppVersion::getVersionCode).orElse(0);
        if (versionCode <= latest) {
            return ResponseEntity.badRequest().body("versionCode must be greater than " + latest);
        }

        Path dir = Paths.get(apkDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String fileName = "winbd-" + versionCode + ".apk";
        Files.copy(apk.getInputStream(), dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

        AppVersion v = new AppVersion();
        v.setVersionName(versionName);
        v.setVersionCode(versionCode);
        v.setForceUpdate(force);
        v.setApkUrl(publicUrl + "/apk/" + fileName);

        return ResponseEntity.ok(repo.save(v));
    }
}
