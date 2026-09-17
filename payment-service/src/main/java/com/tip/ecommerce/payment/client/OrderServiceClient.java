package com.tip.ecommerce.payment.client;

import com.tip.ecommerce.payment.dto.OrderView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OrderServiceClient {

    private final WebClient webClient;
    private final String orderServiceUrl;

    public OrderServiceClient(WebClient webClient,
                               @Value("${services.order-service-url}") String orderServiceUrl) {
        this.webClient = webClient;
        this.orderServiceUrl = orderServiceUrl;
    }

    // Enforces "no payment without an existing order" — a real synchronous
    // call to order-service, not just a conceptual rule. WebClient used as
    // a plain blocking client (.block()) — the rest of this service (JPA,
    // Kafka producer) is blocking too, so there's no end-to-end reactive
    // chain to gain without also moving to R2DBC.
    public OrderView getOrder(Long orderId) {
        try {
            return webClient.get()
                    .uri(orderServiceUrl + "/api/orders/{id}", orderId)
                    .retrieve()
                    .bodyToMono(OrderView.class)
                    .block();
        } catch (WebClientResponseException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + orderId + " not found — cannot take payment for it");
        }
    }
}
