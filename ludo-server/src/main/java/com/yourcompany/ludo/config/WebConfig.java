package com.yourcompany.ludo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // ✅ Serve files from local /avatars/ directory for URL /avatars/**
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("file:" + System.getProperty("user.dir") + "/avatars/");
    }
}
