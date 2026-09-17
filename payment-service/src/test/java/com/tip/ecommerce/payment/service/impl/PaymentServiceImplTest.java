package com.tip.ecommerce.payment.service.impl;

import com.tip.ecommerce.payment.client.OrderServiceClient;
import com.tip.ecommerce.payment.dto.CreatePaymentRequest;
import com.tip.ecommerce.payment.dto.OrderView;
import com.tip.ecommerce.payment.dto.PaymentDto;
import com.tip.ecommerce.payment.entity.Payment;
import com.tip.ecommerce.payment.entity.PaymentStatus;
import com.tip.ecommerce.payment.event.PaymentCompletedEvent;
import com.tip.ecommerce.payment.repository.PaymentRepository;
import com.tip.ecommerce.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderServiceClient orderServiceClient;
    @Mock
    private KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    @InjectMocks
    PaymentServiceImpl paymentService;

    @Test
    public void processPayment_savesAndPublishes_whenNoExistingSuccessPayment() {
        when(paymentRepository.findByOrderIdAndStatus(1L, PaymentStatus.SUCCESS))
                .thenReturn(Optional.empty());
        when(orderServiceClient.getOrder(1L)).thenReturn(new OrderView(1L,"CREATED"));
        when(paymentRepository.save(any())).thenReturn(createPayment());
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        //Calling Actual Business method
        PaymentDto paymentDto = paymentService.processPayment(new CreatePaymentRequest(1L,BigDecimal.TEN));
        assertThat(paymentDto.id()).isEqualTo(100L);
        assertThat(paymentDto.orderId()).isEqualTo(1L);
        verify(kafkaTemplate).send(eq("payment-completed"), eq("1"), any());//Send method should call at least once

    }

    private Payment createPayment() {
        Payment payment = new Payment();
        payment.setId(100L);
        payment.setOrderId(1L);
        payment.setAmount(BigDecimal.TEN);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setCreatedAt(Instant.now());
        return  payment;
    }
}
