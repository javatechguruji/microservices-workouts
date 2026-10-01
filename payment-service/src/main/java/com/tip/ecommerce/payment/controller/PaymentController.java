package com.tip.ecommerce.payment.controller;

import com.tip.ecommerce.payment.dto.CreatePaymentRequest;
import com.tip.ecommerce.payment.dto.PaymentDto;
import com.tip.ecommerce.payment.service.PaymentService;
import com.tip.ecommerce.payment.client.OrderServiceClient;
import com.tip.ecommerce.payment.security.Caller;
import com.tip.ecommerce.payment.security.RequireAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderServiceClient orders;

    public PaymentController(PaymentService paymentService, OrderServiceClient orders) {
        this.paymentService = paymentService;
        this.orders = orders;
    }

    @RequireAccess(permissions="payments:create")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDto createPayment(@RequestBody CreatePaymentRequest request, HttpServletRequest http) {
        authorize(request.orderId(),http);
        return paymentService.processPayment(request);
    }

    @RequireAccess(permissions={"payments:read","payments:read:any"})
    @GetMapping("/{id}")
    public PaymentDto getPayment(@PathVariable Long id, HttpServletRequest http) {
        PaymentDto payment = paymentService.getPayment(id);
        authorize(payment.orderId(),http);
        return payment;
    }
    private void authorize(Long orderId, HttpServletRequest http) {
        if (orderId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"orderId required");
        var order = orders.getOrder(orderId);
        Caller.from(http)
                .requireOwner(order.customerId(),order.tenant(),"payments:read:any");
    }
}
