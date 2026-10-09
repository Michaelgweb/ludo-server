package com.yourcompany.ludo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;

@Service
public class TelegramService {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${telegram.bot-token:}")
    private String token;

    @Value("${telegram.chat-id:}")
    private String chatId;

    /** async: টেলিগ্রাম ফেল/স্লো হলেও ডিপোজিটে কোনো প্রভাব পড়বে না */
    public void send(String text) {
        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) return;
        CompletableFuture.runAsync(() -> {
            try {
                String body = "chat_id=" + URLEncoder.encode(chatId, UTF_8)
                        + "&text=" + URLEncoder.encode(text, UTF_8);
                HttpRequest req = HttpRequest.newBuilder(
                                URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                http.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // টেলিগ্রাম ফেল করলে চুপচাপ ignore
            }
        });
    }
}
