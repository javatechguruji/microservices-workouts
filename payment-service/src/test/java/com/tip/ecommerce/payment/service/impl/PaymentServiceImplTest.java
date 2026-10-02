package com.tip.ecommerce.payment.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tip.ecommerce.payment.client.OrderServiceClient;
import com.tip.ecommerce.payment.dto.*;
import com.tip.ecommerce.payment.entity.*;
import com.tip.ecommerce.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {
  @Mock PaymentRepository payments;
  @Mock OrderServiceClient orders;
  @Mock JdbcTemplate db;
  PaymentServiceImpl service;

  @BeforeEach
  void setup() {
    service = new PaymentServiceImpl(payments, orders, db);
  }

  Payment payment() {
    return Payment.builder()
        .id(10L)
        .orderId(1L)
        .amount(BigDecimal.TEN)
        .status(PaymentStatus.SUCCESS)
        .createdAt(Instant.now())
        .build();
  }

  @Test
  void writesOutboxWithPayment() {
    when(orders.getOrder(1L))
        .thenReturn(new OrderView(1L, "PENDING", "customer1", "demo", BigDecimal.TEN));
    when(payments.save(any())).thenReturn(payment());
    assertThat(service.processPayment(new CreatePaymentRequest(1L, BigDecimal.TEN)).id())
        .isEqualTo(10L);
    verify(db).update(contains("payment_outbox"), eq(1L), eq(10L));
  }

  @Test
  void retryReturnsSamePayment() {
    when(payments.findByOrderIdAndStatus(1L, PaymentStatus.SUCCESS))
        .thenReturn(Optional.of(payment()));
    assertThat(service.processPayment(new CreatePaymentRequest(1L, BigDecimal.TEN)).id())
        .isEqualTo(10L);
    verify(payments, never()).save(any());
    verifyNoInteractions(orders);
  }

  @Test
  void cannotUnderpay() {
    when(orders.getOrder(1L))
        .thenReturn(new OrderView(1L, "PENDING", "customer1", "demo", new BigDecimal("20.00")));
    assertThatThrownBy(() -> service.processPayment(new CreatePaymentRequest(1L, BigDecimal.TEN)))
        .isInstanceOf(ResponseStatusException.class);
    verify(payments, never()).save(any());
  }

  @Test
  void duplicateWithDifferentAmountRejected() {
    when(payments.findByOrderIdAndStatus(1L, PaymentStatus.SUCCESS))
        .thenReturn(Optional.of(payment()));
    assertThatThrownBy(() -> service.processPayment(new CreatePaymentRequest(1L, BigDecimal.ONE)))
        .isInstanceOf(ResponseStatusException.class);
  }
}
