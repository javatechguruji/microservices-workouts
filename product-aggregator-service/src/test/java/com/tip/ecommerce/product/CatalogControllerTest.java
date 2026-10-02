package com.tip.ecommerce.product;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Mono;

class CatalogControllerTest {
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path images;
  @Test
  void quoteCalculatesDiscountedLineTotalsAndRejectsBadQuantity() throws Exception {
    var db = mock(JdbcTemplate.class);
    var gateway = mock(GatewayClient.class);
    var controller = new CatalogController(db, gateway, images.toString());
    when(db.queryForList(anyString()))
        .thenReturn(
            List.of(
                Map.of(
                    "sku",
                    "ELEC-1",
                    "name",
                    "Headphones",
                    "category",
                    "Electronics",
                    "original_price",
                    new BigDecimal("79.00"),
                    "image",
                    "ELEC-1.svg")));
    when(gateway.get(anyString()))
        .thenReturn(
            Mono.just(new ObjectMapper().readTree("[{\"sku\":\"ELEC-1\",\"percent\":15}]")));
    var result =
        controller
            .quote(
                new CatalogController.QuoteRequest(
                    List.of(new CatalogController.Line("ELEC-1", 2))),
                "catalog:quote")
            .block();
    assertThat(result.get("amount")).isEqualTo(new BigDecimal("134.30"));
    assertThatThrownBy(
            () ->
                controller
                    .quote(
                        new CatalogController.QuoteRequest(
                            List.of(new CatalogController.Line("ELEC-1", 0))),
                        "catalog:quote")
                    .block())
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }
}
