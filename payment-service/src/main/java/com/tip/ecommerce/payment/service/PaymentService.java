package com.tip.ecommerce.payment.service;

import com.tip.ecommerce.payment.dto.CreatePaymentRequest;
import com.tip.ecommerce.payment.dto.PaymentDto;

public interface PaymentService {

    PaymentDto processPayment(CreatePaymentRequest request);

    PaymentDto getPayment(Long id);
}
