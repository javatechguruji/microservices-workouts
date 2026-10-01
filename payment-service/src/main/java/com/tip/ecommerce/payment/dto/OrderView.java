package com.tip.ecommerce.payment.dto;
public record OrderView(Long id, String status, String customerId, String tenant) {
    public OrderView(Long id, String status) { this(id,status,null,null); }
}
