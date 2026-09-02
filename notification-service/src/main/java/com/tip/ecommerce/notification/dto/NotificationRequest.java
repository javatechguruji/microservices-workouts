package com.tip.ecommerce.notification.dto;

// Mirrors the "payment-completed" event payload shape from
// docs/kafka-notes.md — this is what a future @KafkaListener will pass in
// after deserializing the Kafka message.
public record NotificationRequest(
        Long orderId,
        String status
) {
}
