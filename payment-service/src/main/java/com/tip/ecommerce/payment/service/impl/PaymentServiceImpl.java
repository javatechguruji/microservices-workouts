package com.tip.ecommerce.payment.service.impl;

import com.tip.ecommerce.payment.client.OrderServiceClient;
import com.tip.ecommerce.payment.dto.CreatePaymentRequest;
import com.tip.ecommerce.payment.dto.PaymentDto;
import com.tip.ecommerce.payment.entity.Payment;
import com.tip.ecommerce.payment.entity.PaymentStatus;
import com.tip.ecommerce.payment.event.PaymentCompletedEvent;
import com.tip.ecommerce.payment.repository.PaymentRepository;
import com.tip.ecommerce.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String TOPIC = "payment-completed";
    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final OrderServiceClient orderServiceClient;
    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                               OrderServiceClient orderServiceClient,
                               KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate) {
        this.paymentRepository = paymentRepository;
        this.orderServiceClient = orderServiceClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public PaymentDto processPayment(CreatePaymentRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be greater than zero");
        }

        // Blocks payment for an order that doesn't exist — the REST
        // enforcement of "you must create an order before you can pay".
        orderServiceClient.getOrder(request.orderId());

        Payment payment = new Payment();
        payment.setOrderId(request.orderId());
        payment.setAmount(request.amount());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setCreatedAt(Instant.now());
        payment = paymentRepository.save(payment);

        // Direct publish, not the transactional outbox pattern (see
        // docs/kafka-notes.md P2) — the DB write and the publish are two
        // separate operations, so a crash between them can drop the event.
        // Fine for this exercise; the outbox pattern is the real fix.
        Long orderId = payment.getOrderId();
        PaymentCompletedEvent event = new PaymentCompletedEvent(orderId, payment.getId(), payment.getStatus().name());
        kafkaTemplate.send(TOPIC, String.valueOf(orderId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for order {}", TOPIC, orderId, ex);
                    }
                });

        return toDto(payment);
    }

    @Override
    public PaymentDto getPayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment " + id + " not found"));
        return toDto(payment);
    }

    private PaymentDto toDto(Payment payment) {
        return new PaymentDto(payment.getId(), payment.getOrderId(), payment.getAmount(), payment.getStatus(), payment.getCreatedAt());
    }
}
