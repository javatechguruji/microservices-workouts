package com.tip.ecommerce.order.observability;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tip.ecommerce.order.client.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

class CommerceGatewayLoggingTest {
  @Test
  void logsSafeOutboundPayloadAndResponseAndPreservesAuthorizationAndWireBody() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var bodySeen = new AtomicReference<String>();
    var authorizationSeen = new AtomicReference<String>();
    server.createContext(
        "/api/inventory/reservations",
        exchange -> {
          bodySeen.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          authorizationSeen.set(exchange.getRequestHeaders().getFirst("Authorization"));
          byte[] response =
              "{\"orderId\":42,\"state\":\"RESERVED\",\"address\":\"PRIVATE_ADDRESS\"}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    var logger = (Logger) LoggerFactory.getLogger(CommerceGateway.class);
    var logs = new ListAppender<ILoggingEvent>();
    logs.start();
    logger.addAppender(logs);
    try {
      var tokens = mock(ServiceTokenClient.class);
      when(tokens.accessToken()).thenReturn("PRIVATE_TOKEN");
      var client =
          new CommerceGateway(
              tokens, new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort());
      var result =
          client.post(
              "/api/inventory/reservations",
              Map.of("orderId", 42, "amount", 22.50, "address", "PRIVATE_ADDRESS"));
      assertThat(result.path("state").asText()).isEqualTo("RESERVED");
      assertThat(bodySeen.get()).contains("PRIVATE_ADDRESS");
      assertThat(authorizationSeen.get()).isEqualTo("Bearer PRIVATE_TOKEN");
      assertThat(logs.list)
          .anySatisfy(
              e ->
                  assertThat(e.getFormattedMessage())
                      .contains("http.client.completed", "22.5", "RESERVED"));
      assertThat(logs.list)
          .allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain("PRIVATE"));
      server.stop(0);
      assertThatThrownBy(() -> client.post("/api/inventory/reservations", Map.of("orderId", 42)))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
      assertThat(logs.list)
          .anySatisfy(
              e ->
                  assertThat(e.getFormattedMessage())
                      .contains("http.client.failed", "42", "ConnectException"));
    } finally {
      server.stop(0);
      logger.detachAppender(logs);
      logs.stop();
    }
  }
}
