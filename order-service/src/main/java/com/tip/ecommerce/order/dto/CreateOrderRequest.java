package com.tip.ecommerce.order.dto;

import java.math.BigDecimal;

public record CreateOrderRequest(
        String customerId,
        BigDecimal amount
) {
}
