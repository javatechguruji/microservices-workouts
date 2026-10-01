package com.tip.ecommerce.order.service.impl;

import com.tip.ecommerce.order.dto.CreateOrderRequest;
import com.tip.ecommerce.order.dto.OrderDto;
import com.tip.ecommerce.order.entity.Order;
import com.tip.ecommerce.order.entity.OrderStatus;
import com.tip.ecommerce.order.repository.OrderRepository;
import com.tip.ecommerce.order.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;

    public OrderServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public OrderDto createOrder(CreateOrderRequest request, String tenant) {
        if (request.customerId() == null || !request.customerId().matches("[a-zA-Z0-9@._:-]{1,200}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid customer username is required");
        }
        if (request.amount() == null || request.amount().signum() <= 0
                || request.amount().compareTo(new java.math.BigDecimal("99999999.99")) > 0
                || request.amount().stripTrailingZeros().scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive with at most two decimal places");
        }
        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setTenant(tenant);
        order.setAmount(request.amount());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());

        return toDto(orderRepository.save(order));
    }

    @Override
    public OrderDto getOrder(Long id) {
        return toDto(findOrThrow(id));
    }

    @Override
    public List<OrderDto> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public OrderDto updateStatus(Long id, OrderStatus status) {
        if (status == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        Order order = findOrThrow(id);
        order.setStatus(status);
        return toDto(orderRepository.save(order));
    }

    private Order findOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + id + " not found"));
    }

    private OrderDto toDto(Order order) {
        return new OrderDto(order.getId(), order.getCustomerId(), order.getAmount(), order.getStatus(), order.getCreatedAt(), order.getTenant());
    }
}
