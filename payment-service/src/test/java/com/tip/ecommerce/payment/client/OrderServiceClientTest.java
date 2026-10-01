package com.tip.ecommerce.payment.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServiceClientTest {
    private final ServiceTokenClient tokens = mock(ServiceTokenClient.class);

    @Test void sendsMachineBearerTokenToGatewayOrderRoute() {
        when(tokens.accessToken()).thenReturn("machine-token");
        var web = WebClient.builder().exchangeFunction(request -> {
            assertThat(request.url().toString()).isEqualTo("http://gateway:9100/api/orders/7");
            assertThat(request.headers().getFirst("Authorization")).isEqualTo("Bearer machine-token");
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                    .body("{\"id\":7,\"status\":\"PENDING\",\"customerId\":\"customer1\",\"tenant\":\"demo\"}").build());
        }).build();
        var order = new OrderServiceClient(web, "http://gateway:9100", tokens).getOrder(7L);
        assertThat(order.customerId()).isEqualTo("customer1");
        assertThat(order.tenant()).isEqualTo("demo");
    }

    @Test void upstreamTenantDenialIsForbidden() {
        assertMappedStatus(HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN);
    }

    @Test void rejectedMachineTokenIsBadGateway() {
        assertMappedStatus(HttpStatus.UNAUTHORIZED, HttpStatus.BAD_GATEWAY);
    }

    private void assertMappedStatus(HttpStatus upstream, HttpStatus expected) {
        when(tokens.accessToken()).thenReturn("machine-token");
        var web = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(upstream).build())).build();
        assertThatThrownBy(() -> new OrderServiceClient(web, "http://gateway:9100", tokens).getOrder(7L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(expected));
    }
}
