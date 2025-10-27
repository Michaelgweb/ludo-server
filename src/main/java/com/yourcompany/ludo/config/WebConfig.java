package com.yourcompany.ludo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.upload-url-path:/avatars}")
    private String uploadUrlPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /avatars/** ইউআরএল আসলে uploadDir ফোল্ডার থেকে ফাইল সার্ভ করবে
        registry.addResourceHandler(uploadUrlPath + "/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
