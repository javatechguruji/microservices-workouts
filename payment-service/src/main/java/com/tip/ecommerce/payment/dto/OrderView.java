package com.tip.ecommerce.payment.dto;

// Minimal shape of what order-service's GET /api/orders/{id} returns —
// only the fields payment-service actually needs. Spring's default Jackson
// config ignores the extra fields order-service sends back.
public record OrderView(
        Long id,
        String status
) {
}
