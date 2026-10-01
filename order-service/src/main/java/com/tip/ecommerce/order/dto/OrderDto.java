package com.tip.ecommerce.order.dto;

import com.tip.ecommerce.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderDto(
        Long id,
        String customerId,
        BigDecimal amount,
        OrderStatus status,
        Instant createdAt,
        String tenant
) {
}
