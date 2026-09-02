package com.tip.ecommerce.notification.messaging;

import com.tip.ecommerce.notification.dto.NotificationRequest;
import com.tip.ecommerce.notification.event.PaymentCompletedEvent;
import com.tip.ecommerce.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

// No dedup-by-message-id here — this service has no DB (see
// NotificationServiceImpl). A redelivered message after a crash-before-ack
// means an occasional duplicate log line, not a duplicate side effect with
// real consequences; not worth a database for that (docs/kafka-notes.md C1).
@Component
public class PaymentCompletedListener {

    private final NotificationService notificationService;

    public PaymentCompletedListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "payment-completed", groupId = "notification-group")
    public void handlePaymentCompleted(PaymentCompletedEvent event, Acknowledgment ack) {
        notificationService.send(new NotificationRequest(event.orderId(), event.status()));
        ack.acknowledge();
    }
}
