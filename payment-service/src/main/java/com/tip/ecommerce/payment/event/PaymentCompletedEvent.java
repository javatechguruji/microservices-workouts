package com.tip.ecommerce.payment.event;

// Published to the "payment-completed" Kafka topic, keyed by orderId.
// Each consuming service (order-service, notification-service) keeps its
// own copy of this shape rather than sharing a JAR — that's deliberate,
// not an oversight: it's what keeps the services independently deployable.
public record PaymentCompletedEvent(
        Long orderId,
        Long paymentId,
        String status
) {
}
