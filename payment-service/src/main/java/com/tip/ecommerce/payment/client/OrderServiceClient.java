package com.tip.ecommerce.payment.client;

import com.tip.ecommerce.payment.dto.OrderView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OrderServiceClient {

    private final RestTemplate restTemplate;
    private final String orderServiceUrl;

    public OrderServiceClient(RestTemplate restTemplate,
                               @Value("${services.order-service-url}") String orderServiceUrl) {
        this.restTemplate = restTemplate;
        this.orderServiceUrl = orderServiceUrl;
    }

    // Enforces "no payment without an existing order" — a real synchronous
    // call to order-service, not just a conceptual rule.
    public OrderView getOrder(Long orderId) {
        try {
            return restTemplate.getForObject(orderServiceUrl + "/api/orders/{id}", OrderView.class, orderId);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + orderId + " not found — cannot take payment for it");
        }
    }
}
