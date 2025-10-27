package com.yourcompany.ludo.service;

import com.yourcompany.ludo.dto.NotificationDto;
import com.yourcompany.ludo.dto.WithdrawRequestDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendNotification(Long userId, String title, String message) {
        NotificationDto notification = new NotificationDto(
                title,
                message,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notification",
                notification
        );
    }

    public void sendWebSocketNotification(Long userId, String topic, WithdrawRequestDto dto) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                topic,
                dto
        );
    }
}
