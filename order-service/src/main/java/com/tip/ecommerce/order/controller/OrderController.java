package com.tip.ecommerce.order.controller;

import com.tip.ecommerce.order.dto.CreateOrderRequest;
import com.tip.ecommerce.order.dto.OrderDto;
import com.tip.ecommerce.order.dto.UpdateOrderStatusRequest;
import com.tip.ecommerce.order.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto createOrder(@RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/{id}")
    public OrderDto getOrder(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    // Manual stand-in for what a future @KafkaListener on "payment-completed"
    // will do automatically — lets you test the status transition before
    // Kafka is wired up.
    @PatchMapping("/{id}/status")
    public OrderDto updateStatus(@PathVariable Long id, @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.status());
    }
}
