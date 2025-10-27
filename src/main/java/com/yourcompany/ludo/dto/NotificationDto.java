package com.yourcompany.ludo.dto;

public class NotificationDto {
    private String title;
    private String message;
    private String timestamp;

    public NotificationDto() {
    }

    public NotificationDto(String title, String message, String timestamp) {
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
    }

    // Getters & Setters
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }

    public String getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
