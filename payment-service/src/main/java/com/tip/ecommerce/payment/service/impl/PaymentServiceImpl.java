package com.tip.ecommerce.payment.service.impl;

import com.tip.ecommerce.payment.client.OrderServiceClient;
import com.tip.ecommerce.payment.dto.CreatePaymentRequest;
import com.tip.ecommerce.payment.dto.PaymentDto;
import com.tip.ecommerce.payment.entity.Payment;
import com.tip.ecommerce.payment.entity.PaymentStatus;
import com.tip.ecommerce.payment.repository.PaymentRepository;
import com.tip.ecommerce.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderServiceClient orderServiceClient;

    public PaymentServiceImpl(PaymentRepository paymentRepository, OrderServiceClient orderServiceClient) {
        this.paymentRepository = paymentRepository;
        this.orderServiceClient = orderServiceClient;
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

        // TODO Kafka: publish to topic "payment-completed", keyed by
        // orderId, payload e.g. {"orderId":..,"paymentId":..,"status":"SUCCESS"}.
        // order-service and notification-service both consume this topic
        // (see docs/kafka-notes.md).

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
