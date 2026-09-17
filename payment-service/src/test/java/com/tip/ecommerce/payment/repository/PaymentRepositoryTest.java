package com.tip.ecommerce.payment.repository;

import com.tip.ecommerce.payment.entity.Payment;
import com.tip.ecommerce.payment.entity.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class PaymentRepositoryTest {

    @Autowired
    PaymentRepository paymentRepository;

    @Test
    public void testFindByOrderIdAndStatus(){
        Payment payment = createPayment(123L,PaymentStatus.SUCCESS);
        Optional<Payment> existingPayment = paymentRepository.findByOrderIdAndStatus(123L, PaymentStatus.SUCCESS);
        assertThat(existingPayment).isPresent();
        assertThat(existingPayment.get().getId()).isEqualTo(payment.getId());
    }

    private Payment createPayment(long orderId, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(BigDecimal.TEN);
        payment.setStatus(status);
        payment.setCreatedAt(Instant.now());
        return  paymentRepository.save(payment);
    }

}
