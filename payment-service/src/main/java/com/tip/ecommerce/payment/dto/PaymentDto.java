package com.tip.ecommerce.payment.dto;

import com.tip.ecommerce.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentDto(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        Instant createdAt
) {
}
