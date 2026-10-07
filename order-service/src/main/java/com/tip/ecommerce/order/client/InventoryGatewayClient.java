package com.tip.ecommerce.order.client;

import com.fasterxml.jackson.databind.*;
import com.tip.ecommerce.order.observability.OperationalLog;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InventoryGatewayClient {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(InventoryGatewayClient.class);
  private final ServiceTokenClient tokens;
  private final ObjectMapper mapper;
  private final String gateway;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  public InventoryGatewayClient(
      ServiceTokenClient tokens,
      ObjectMapper mapper,
      @Value("${services.gateway-url:http://localhost:9100}") String gateway) {
    this.tokens = tokens;
    this.mapper = mapper;
    this.gateway = gateway;
  }

  public JsonNode get(String sku) {
    long started = System.nanoTime();
    try {
      String url = gateway + "/api/inventory/" + URLEncoder.encode(sku, StandardCharsets.UTF_8);
      var response =
          http.send(
              HttpRequest.newBuilder(URI.create(url))
                  .timeout(Duration.ofSeconds(8))
                  .header("Authorization", "Bearer " + tokens.accessToken())
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      OperationalLog.write(
          LOG,
          response.statusCode() >= 400 ? Level.WARN : Level.DEBUG,
          "http.client.completed",
          "method",
          "GET",
          "path",
          "/api/inventory/{sku}",
          "sku",
          OperationalLog.clean(sku),
          "status",
          response.statusCode(),
          "durationMs",
          (System.nanoTime() - started) / 1_000_000,
          "response",
          OperationalLog.jsonSummary(response.body()));
      if (response.statusCode() != 200)
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Inventory request rejected");
      return mapper.readTree(response.body());
    } catch (InterruptedException ex) {
      OperationalLog.write(
          LOG,
          Level.WARN,
          "http.client.failed",
          "path",
          "/api/inventory/{sku}",
          "failure",
          OperationalLog.failure(ex));
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Inventory request interrupted");
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      OperationalLog.write(
          LOG,
          Level.WARN,
          "http.client.failed",
          "path",
          "/api/inventory/{sku}",
          "failure",
          OperationalLog.failure(ex));
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Inventory unavailable");
    }
  }
}
