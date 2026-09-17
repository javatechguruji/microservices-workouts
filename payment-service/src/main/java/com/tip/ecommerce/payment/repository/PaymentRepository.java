package com.tip.ecommerce.payment.repository;

import com.tip.ecommerce.payment.entity.Payment;
import com.tip.ecommerce.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderIdAndStatus(long orderId, PaymentStatus status);
}
