package com.tip.ecommerce.order.service;

import com.tip.ecommerce.order.dto.CreateOrderRequest;
import com.tip.ecommerce.order.repository.OrderRepository;
import com.tip.ecommerce.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderInputValidationTest {
    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderServiceImpl service = new OrderServiceImpl(repository);

    @Test void rejectsInvalidAmountsBeforePersisting() {
        for (BigDecimal amount : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-1"),
                new BigDecimal("1.001"), new BigDecimal("100000000")}) {
            assertThatThrownBy(() -> service.createOrder(new CreateOrderRequest("customer1", amount), "demo"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("400 BAD_REQUEST");
        }
        verifyNoInteractions(repository);
    }
    @Test void rejectsBlankCustomerAndMissingStatusBeforePersisting() {
        assertThatThrownBy(() -> service.createOrder(new CreateOrderRequest(" ", BigDecimal.ONE), "demo"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400 BAD_REQUEST");
        assertThatThrownBy(() -> service.updateStatus(1L, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400 BAD_REQUEST");
        verifyNoInteractions(repository);
    }
}
