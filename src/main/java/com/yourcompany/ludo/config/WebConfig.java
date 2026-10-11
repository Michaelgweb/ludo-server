package com.yourcompany.ludo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.upload-url-path:/avatars}")
    private String uploadUrlPath;

    @Value("${app.apk.dir:apk}")
    private String apkDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // ---------- অ্যাভাটার ----------
        Path avatarDir = prepareDir(uploadDir);
        registry.addResourceHandler(uploadUrlPath + "/**")
                .addResourceLocations(avatarDir.toUri().toString());

        // ---------- APK ----------
        Path apkPath = prepareDir(apkDir);
        registry.addResourceHandler("/apk/**")
                .addResourceLocations(apkPath.toUri().toString());
    }

    private Path prepareDir(String dirName) {
        Path dir = Paths.get(dirName).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create dir: " + dir, e);
        }
        return dir;
    }
}
