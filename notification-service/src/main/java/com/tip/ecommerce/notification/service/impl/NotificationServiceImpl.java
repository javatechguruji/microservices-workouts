package com.tip.ecommerce.notification.service.impl;

import com.tip.ecommerce.notification.dto.NotificationRequest;
import com.tip.ecommerce.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// No DB here on purpose — this service has nothing worth persisting yet.
// The real entry point will be a @KafkaListener on "payment-completed"
// (see docs/kafka-notes.md) calling this same method; the controller is a
// manual stand-in so this path can be tested before Kafka is wired.
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Override
    public void send(NotificationRequest request) {
        log.info("Notification: order {} payment {}", request.orderId(), request.status());
    }
}
