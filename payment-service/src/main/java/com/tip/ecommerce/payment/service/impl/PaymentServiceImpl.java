package com.tip.ecommerce.payment.service.impl;

import com.tip.ecommerce.payment.client.OrderServiceClient;
import com.tip.ecommerce.payment.dto.CreatePaymentRequest;
import com.tip.ecommerce.payment.dto.PaymentDto;
import com.tip.ecommerce.payment.entity.Payment;
import com.tip.ecommerce.payment.entity.PaymentStatus;
import com.tip.ecommerce.payment.observability.OperationalLog;
import com.tip.ecommerce.payment.repository.PaymentRepository;
import com.tip.ecommerce.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentServiceImpl implements PaymentService {

  private static final String TOPIC = "payment-completed";
  private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

  private final PaymentRepository paymentRepository;
  private final OrderServiceClient orderServiceClient;
  private final org.springframework.jdbc.core.JdbcTemplate db;

  public PaymentServiceImpl(
      PaymentRepository paymentRepository,
      OrderServiceClient orderServiceClient,
      org.springframework.jdbc.core.JdbcTemplate db) {
    this.paymentRepository = paymentRepository;
    this.orderServiceClient = orderServiceClient;
    this.db = db;
  }

  @Override
  @org.springframework.transaction.annotation.Transactional
  public PaymentDto processPayment(CreatePaymentRequest request) {
    if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be greater than zero");
    }

    // Database lock serializes duplicate calls across instances. A lost response is safe to retry.
    if (request.orderId() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order required");
    db.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, request.orderId());
    var previous =
        paymentRepository.findByOrderIdAndStatus(request.orderId(), PaymentStatus.SUCCESS);
    if (previous.isPresent()) {
      if (previous.get().getAmount().compareTo(request.amount()) != 0)
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment amount differs");
      OperationalLog.write(
          log,
          Level.INFO,
          "payment.replayed",
          "orderId",
          request.orderId(),
          "amount",
          request.amount(),
          "paymentId",
          previous.get().getId());
      return toDto(previous.get());
    }
    var order = orderServiceClient.getOrder(request.orderId());
    if (!"PENDING".equals(order.status())
        || order.amount() == null
        || order.amount().compareTo(request.amount()) != 0)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Payment must match the pending order total");

    Payment payment = new Payment();
    payment.setOrderId(request.orderId());
    payment.setAmount(request.amount());
    payment.setStatus(PaymentStatus.SUCCESS);
    payment.setCreatedAt(Instant.now());
    payment = paymentRepository.save(payment);

    db.update(
        "INSERT INTO payment_outbox(order_id,payment_id) VALUES (?,?) ON CONFLICT DO NOTHING",
        payment.getOrderId(),
        payment.getId());

    OperationalLog.afterCommit(
        log,
        "payment.completed",
        "orderId",
        payment.getOrderId(),
        "paymentId",
        payment.getId(),
        "amount",
        payment.getAmount(),
        "status",
        payment.getStatus());
    return toDto(payment);
  }

  @Override
  public PaymentDto getPayment(Long id) {
    Payment payment =
        paymentRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment " + id + " not found"));
    return toDto(payment);
  }

  private PaymentDto toDto(Payment payment) {
    return new PaymentDto(
        payment.getId(),
        payment.getOrderId(),
        payment.getAmount(),
        payment.getStatus(),
        payment.getCreatedAt());
  }
}
