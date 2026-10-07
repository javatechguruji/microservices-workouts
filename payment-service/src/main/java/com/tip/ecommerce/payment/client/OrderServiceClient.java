package com.tip.ecommerce.payment.client;

import com.tip.ecommerce.payment.dto.OrderView;
import com.tip.ecommerce.payment.observability.OperationalLog;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
public class OrderServiceClient {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(OrderServiceClient.class);

  private final WebClient webClient;
  private final String orderServiceUrl;
  private final ServiceTokenClient tokens;

  public OrderServiceClient(
      WebClient webClient,
      @Value("${services.gateway-url:http://localhost:9100}") String orderServiceUrl,
      ServiceTokenClient tokens) {
    this.tokens = tokens;
    this.webClient = webClient;
    this.orderServiceUrl = orderServiceUrl;
  }

  // Enforces "no payment without an existing order" — a real synchronous
  // call to order-service, not just a conceptual rule. WebClient used as
  // a plain blocking client (.block()) — the rest of this service (JPA,
  // Kafka producer) is blocking too, so there's no end-to-end reactive
  // chain to gain without also moving to R2DBC.
  public OrderView getOrder(Long orderId) {
    long started = System.nanoTime();
    var responseStatus = new java.util.concurrent.atomic.AtomicInteger();
    OperationalLog.write(
        LOG,
        Level.DEBUG,
        "http.client.started",
        "method",
        "GET",
        "path",
        "/api/orders/{id}",
        "orderId",
        orderId);
    try {
      return webClient
          .get()
          .uri(orderServiceUrl + "/api/orders/{id}", orderId)
          .headers(h -> h.setBearerAuth(tokens.accessToken()))
          .exchangeToMono(
              response -> {
                responseStatus.set(response.statusCode().value());
                if (response.statusCode().isError())
                  return response.createException().flatMap(Mono::error);
                return response.bodyToMono(OrderView.class);
              })
          .doOnNext(
              body ->
                  OperationalLog.write(
                      LOG,
                      Level.DEBUG,
                      "http.client.completed",
                      "method",
                      "GET",
                      "path",
                      "/api/orders/{id}",
                      "orderId",
                      orderId,
                      "status",
                      responseStatus.get(),
                      "durationMs",
                      (System.nanoTime() - started) / 1_000_000,
                      "response",
                      OperationalLog.summary(body)))
          .block(java.time.Duration.ofSeconds(8));
    } catch (RuntimeException e) {
      OperationalLog.write(
          LOG,
          Level.WARN,
          "http.client.failed",
          "method",
          "GET",
          "path",
          "/api/orders/{id}",
          "orderId",
          orderId,
          "status",
          e instanceof WebClientResponseException r ? r.getStatusCode().value() : 0,
          "durationMs",
          (System.nanoTime() - started) / 1_000_000,
          "failure",
          OperationalLog.failure(e));
      if (e instanceof WebClientResponseException.Forbidden) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Payment service cannot access this order's tenant");
      } else if (e instanceof WebClientResponseException.Unauthorized) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "Gateway rejected payment service identity");
      } else if (e instanceof WebClientResponseException.NotFound) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Order " + orderId + " not found — cannot take payment for it");
      }
      throw e;
    }
  }
}
