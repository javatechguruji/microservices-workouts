package com.tip.ecommerce.gateway.observability;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class OperationalLogTest {
  public record Payload(
      long orderId,
      BigDecimal amount,
      String address,
      String customerId,
      String access_token,
      List<Map<String, Object>> items) {}

  @Test
  void retainsBusinessFieldsButDropsSecretsAndPersonalDataAtEveryLevel() throws Exception {
    var payload =
        new Payload(
            42,
            new BigDecimal("22.50"),
            "PRIVATE_ADDRESS",
            "PRIVATE_CUSTOMER",
            "PRIVATE_TOKEN",
            List.of(Map.of("sku", "BOOK-1", "quantity", 2, "password", "PRIVATE_PASSWORD")));
    String result = new ObjectMapper().writeValueAsString(OperationalLog.summary(payload));
    assertThat(result)
        .contains("42", "22.50", "BOOK-1", "quantity")
        .doesNotContain("PRIVATE", "address", "customerId", "access_token", "password");
    assertThat(
            OperationalLog.jsonSummary(
                    "{\"orderId\":42,\"items\":[{\"sku\":\"BOOK-1\",\"secret\":\"PRIVATE\"}],\"Authorization\":\"PRIVATE\"}")
                .toString())
        .contains("42", "BOOK-1")
        .doesNotContain("PRIVATE", "Authorization", "secret");
  }

  @Test
  void capsCollectionsDepthAndTextAndDoesNotLeakMalformedBodies() {
    assertThat(
            OperationalLog.summary(Collections.nCopies(1000, Map.of("sku", "X".repeat(2000))))
                .toString())
        .hasSizeLessThan(2200);
    Map<String, Object> cyclic = new HashMap<>();
    cyclic.put("items", cyclic);
    assertThat(OperationalLog.summary(cyclic).toString()).contains("limited");
    assertThat(OperationalLog.jsonSummary("PRIVATE_NOT_JSON")).isEqualTo("[non-JSON body omitted]");
    assertThat(OperationalLog.jsonSummary("x".repeat(65537))).isEqualTo("[body omitted]");
    assertThat(OperationalLog.jsonSummary("\"PRIVATE_TEXT\"")).isEqualTo("[text omitted]");
    assertThat(OperationalLog.summary("PRIVATE_TEXT")).isEqualTo("[text omitted]");
    assertThat(OperationalLog.summary(Map.of("sku", "BOOK-1\nFORGED")).toString())
        .doesNotContain("\n");
  }

  @Test
  void removesQueryIdentityAndExceptionMessages() {
    assertThat(OperationalLog.path("/api/customers/private%40email/preferences?token=PRIVATE"))
        .isEqualTo("/api/customers/{customer}/preferences");
    var failure =
        new IllegalStateException(
            "PRIVATE_PASSWORD", new java.net.ConnectException("PRIVATE_HOST"));
    assertThat(OperationalLog.failure(failure))
        .contains("IllegalStateException", "ConnectException")
        .doesNotContain("PRIVATE");
  }
}
