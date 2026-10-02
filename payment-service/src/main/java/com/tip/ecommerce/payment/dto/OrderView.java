package com.tip.ecommerce.payment.dto;

import java.math.BigDecimal;

public record OrderView(
    Long id, String status, String customerId, String tenant, BigDecimal amount) {
  public OrderView(Long id, String status) {
    this(id, status, null, null, null);
  }

  public OrderView(Long id, String status, String customerId, String tenant) {
    this(id, status, customerId, tenant, null);
  }
}
