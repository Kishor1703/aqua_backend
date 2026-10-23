package com.aquaalert.notification;

public record SendResult(boolean success, String providerMessageId, String errorMessage) {
    public static SendResult ok(String providerMessageId) {
        return new SendResult(true, providerMessageId, null);
    }

    public static SendResult failure(String errorMessage) {
        return new SendResult(false, null, errorMessage);
    }
}
