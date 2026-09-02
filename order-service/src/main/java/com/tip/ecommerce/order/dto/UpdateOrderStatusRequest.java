package com.tip.ecommerce.order.dto;

import com.tip.ecommerce.order.entity.OrderStatus;

public record UpdateOrderStatusRequest(
        OrderStatus status
) {
}
