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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // রিলেটিভ পাথ (যেমন "avatars") কে পুরো পাথে রূপান্তর, UserController এও একই নিয়ম
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create upload dir: " + dir, e);
        }

        // /avatars/** ইউআরএল আসলে এই ফোল্ডার থেকে ফাইল সার্ভ করবে
        // dir.toUri() এর শেষে নিজে থেকেই "/" থাকে (ফোল্ডার আগে তৈরি করা হয়েছে)
        registry.addResourceHandler(uploadUrlPath + "/**")
                .addResourceLocations(dir.toUri().toString());
    }
}
