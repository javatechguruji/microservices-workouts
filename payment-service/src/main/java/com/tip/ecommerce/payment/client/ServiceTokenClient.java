package com.tip.ecommerce.payment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tip.ecommerce.payment.observability.OperationalLog;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ServiceTokenClient {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ServiceTokenClient.class);
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final ObjectMapper mapper;
  private final String tokenUrl, clientId, clientSecret;
  private String token;
  private Instant usableUntil = Instant.EPOCH;

  public ServiceTokenClient(
      ObjectMapper mapper,
      @Value(
              "${security.client.token-uri:http://localhost:8180/realms/ecommerce/protocol/openid-connect/token}")
          String tokenUrl,
      @Value("${security.client.id:payment-service}") String clientId,
      @Value("${security.client.secret:}") String clientSecret) {
    this.mapper = mapper;
    this.tokenUrl = tokenUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  public synchronized String accessToken() {
    if (token != null && Instant.now().isBefore(usableUntil)) return token;
    if (clientSecret.isBlank())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Service credential not configured");
    try {
      String body =
          "grant_type=client_credentials&client_id="
              + encode(clientId)
              + "&client_secret="
              + encode(clientSecret);
      HttpResponse<String> response =
          http.send(
              HttpRequest.newBuilder(URI.create(tokenUrl))
                  .timeout(Duration.ofSeconds(5))
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .POST(HttpRequest.BodyPublishers.ofString(body))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      OperationalLog.write(
          LOG,
          response.statusCode() == 200 ? Level.DEBUG : Level.WARN,
          "auth.token.response",
          "status",
          response.statusCode());
      if (response.statusCode() != 200)
        throw new IllegalStateException("Token endpoint rejected request");
      JsonNode json = mapper.readTree(response.body());
      String next = json.path("access_token").asText();
      if (next.isBlank()) throw new IllegalStateException("Missing access token");
      token = next;
      usableUntil = Instant.now().plusSeconds(Math.max(0, json.path("expires_in").asLong() - 30));
      return token;
    } catch (InterruptedException ex) {
      OperationalLog.write(
          LOG, Level.WARN, "auth.token.failed", "failure", OperationalLog.failure(ex));
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Token request interrupted");
    } catch (Exception ex) {
      OperationalLog.write(
          LOG, Level.WARN, "auth.token.failed", "failure", OperationalLog.failure(ex));
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Unable to obtain service token");
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
