package com.tip.ecommerce.notification.event;

// notification-service's own copy of the payload payment-service publishes
// to "payment-completed" — see the equivalent record in payment-service and
// docs/kafka-notes.md.
public record PaymentCompletedEvent(
        Long orderId,
        Long paymentId,
        String status
) {
}
