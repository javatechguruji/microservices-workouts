package com.tip.ecommerce.order.service;

import com.tip.ecommerce.order.dto.CreateOrderRequest;
import com.tip.ecommerce.order.dto.OrderDto;
import com.tip.ecommerce.order.entity.OrderStatus;

import java.util.List;

public interface OrderService {

    OrderDto createOrder(CreateOrderRequest request, String tenant);

    OrderDto getOrder(Long id);

    List<OrderDto> getAllOrders();

    // Called by the manual PATCH endpoint today; this is also the method a
    // future @KafkaListener on "payment-completed" should call to move the
    // order from PENDING to CONFIRMED/FAILED once payment-service reports
    // the outcome.
    OrderDto updateStatus(Long id, OrderStatus status);
}
