package com.tip.ecommerce.order.client;

import com.fasterxml.jackson.databind.*;
import com.tip.ecommerce.order.observability.OperationalLog;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CommerceGateway {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CommerceGateway.class);
  private final ServiceTokenClient tokens;
  private final ObjectMapper json;
  private final String gateway;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  public CommerceGateway(
      ServiceTokenClient tokens,
      ObjectMapper json,
      @Value("${services.gateway-url}") String gateway) {
    this.tokens = tokens;
    this.json = json;
    this.gateway = gateway;
  }

  public JsonNode get(String path) {
    return call(path, null);
  }

  public JsonNode post(String path, Object body) {
    return call(path, body);
  }

  private JsonNode call(String path, Object body) {
    long started = System.nanoTime();
    String method = body == null ? "GET" : "POST";
    String route = OperationalLog.path(path);
    Object request = OperationalLog.summary(body);
    OperationalLog.write(
        LOG,
        Level.DEBUG,
        "http.client.started",
        "method",
        method,
        "path",
        route,
        "request",
        request);
    try {
      var builder =
          HttpRequest.newBuilder(URI.create(gateway + path))
              .timeout(Duration.ofSeconds(8))
              .header("Authorization", "Bearer " + tokens.accessToken())
              .header("Content-Type", "application/json")
              .method(
                  body == null ? "GET" : "POST",
                  body == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
      var req = builder.build();
      var response = http.send(req, HttpResponse.BodyHandlers.ofString());
      OperationalLog.write(
          LOG,
          response.statusCode() >= 400 ? Level.WARN : body == null ? Level.DEBUG : Level.INFO,
          "http.client.completed",
          "method",
          method,
          "path",
          route,
          "status",
          response.statusCode(),
          "durationMs",
          (System.nanoTime() - started) / 1_000_000,
          "request",
          request,
          "response",
          OperationalLog.jsonSummary(response.body()));
      if (response.statusCode() >= 400)
        throw new ResponseStatusException(
            HttpStatus.valueOf(response.statusCode()), "Dependency rejected " + path);
      return json.readTree(response.body());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (InterruptedException e) {
      OperationalLog.write(
          LOG,
          Level.WARN,
          "http.client.failed",
          "method",
          method,
          "path",
          route,
          "request",
          request,
          "durationMs",
          (System.nanoTime() - started) / 1_000_000,
          "failure",
          OperationalLog.failure(e));
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Dependency interrupted");
    } catch (Exception e) {
      OperationalLog.write(
          LOG,
          Level.WARN,
          "http.client.failed",
          "method",
          method,
          "path",
          route,
          "request",
          request,
          "durationMs",
          (System.nanoTime() - started) / 1_000_000,
          "failure",
          OperationalLog.failure(e));
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Dependency unavailable; safe to retry");
    }
  }
}
