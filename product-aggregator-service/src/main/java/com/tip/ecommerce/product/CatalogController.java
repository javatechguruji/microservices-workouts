package com.tip.ecommerce.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/products")
public class CatalogController {
  private final JdbcTemplate db;
  private final GatewayClient gateway;
  private final Path images;

  public CatalogController(
      JdbcTemplate db, GatewayClient gateway, @Value("${catalog.images:}") String images) {
    this.db = db;
    this.db.setQueryTimeout(3);
    this.gateway = gateway;
    this.images = ProductImageDirectory.resolve(images, Path.of(""));
    org.slf4j.LoggerFactory.getLogger(CatalogController.class)
        .info("Serving product images from {}", this.images);
  }

  private Mono<List<Map<String, Object>>> products() {
    return Mono.fromCallable(() -> db.queryForList("SELECT * FROM product ORDER BY category,sku"))
        .subscribeOn(Schedulers.boundedElastic());
  }

  @GetMapping
  public Mono<Map<String, Object>> catalog(
      @RequestHeader("X-Auth-Username") String username,
      @RequestHeader("X-Auth-Tenant") String tenant) {
    // Customer ID comes from authenticated identity, never an untrusted query parameter.
    if (!tenant.equals("demo"))
      return Mono.error(
          new ResponseStatusException(
              HttpStatus.FORBIDDEN, "Catalog service account is scoped to demo tenant"));
    return products()
        .flatMap(
            rows -> {
              String skus =
                  String.join(",", rows.stream().map(r -> (String) r.get("sku")).toList());
              var profile =
                  gateway.get(
                      "/api/customers/"
                          + org.springframework.web.util.UriUtils.encodePathSegment(
                              username, java.nio.charset.StandardCharsets.UTF_8)
                          + "/preferences");
              var discounts = gateway.get("/api/discounts?skus=" + skus);
              // Ratings are optional enrichment; prices and customer identity fail closed.
              var ratings =
                  gateway
                      .get("/api/ratings?skus=" + skus)
                      .onErrorReturn(JsonNodeFactory.instance.arrayNode());
              var availability =
                  gateway
                      .get("/api/inventory?skus=" + skus)
                      .onErrorReturn(JsonNodeFactory.instance.arrayNode());
              return Mono.zip(profile, discounts, ratings, availability)
                  .map(
                      t -> {
                        var preferred = new ArrayList<String>();
                        t.getT1().path("categories").forEach(c -> preferred.add(c.asText()));
                        var cards = enrich(rows, t.getT2(), t.getT3());
                        for (var card : cards) {
                          card.put("available", null);
                          for (var stock : t.getT4())
                            if (stock.path("sku").asText().equals(card.get("sku")))
                              card.put("available", stock.path("available").asInt());
                        }
                        var categories =
                            new ArrayList<>(
                                List.of("Electronics", "Groceries", "Home", "Books", "Fitness"));
                        categories.sort(
                            Comparator.comparingInt(
                                c -> preferred.contains(c) ? preferred.indexOf(c) : -1 + 100));
                        return Map.<String, Object>of(
                            "categories",
                            categories.stream()
                                .map(
                                    c ->
                                        Map.of(
                                            "name",
                                            c,
                                            "preferred",
                                            preferred.contains(c),
                                            "products",
                                            cards.stream()
                                                .filter(p -> p.get("category").equals(c))
                                                .toList()))
                                .toList(),
                            "reason",
                            t.getT1().path("reason").asText(),
                            "currency",
                            "USD");
                      });
            });
  }

  public record Line(String sku, int quantity) {}

  public record QuoteRequest(List<Line> items) {}

  @PostMapping("/quote")
  public Mono<Map<String, Object>> quote(
      @RequestBody QuoteRequest req,
      @RequestHeader(value = "X-Auth-Permissions", defaultValue = "") String permissions) {
    if (!Arrays.asList(permissions.split(",")).contains("catalog:quote"))
      return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN));
    if (req.items() == null
        || req.items().isEmpty()
        || req.items().size() > 50
        || req.items().stream()
            .anyMatch(i -> i == null || i.sku() == null || i.quantity() < 1 || i.quantity() > 99)
        || req.items().stream().map(Line::sku).distinct().count() != req.items().size())
      return Mono.error(
          new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "Unique SKUs and quantities 1–99 required"));
    com.tip.ecommerce.product.observability.OperationalLog.write(
        org.slf4j.LoggerFactory.getLogger(getClass()),
        org.slf4j.event.Level.DEBUG,
        "quote.requested",
        "request",
        com.tip.ecommerce.product.observability.OperationalLog.summary(req));
    return products()
        .flatMap(
            rows ->
                gateway
                    .get(
                        "/api/discounts?skus="
                            + String.join(
                                ",", rows.stream().map(r -> (String) r.get("sku")).toList()))
                    .map(
                        d -> {
                          var all = enrich(rows, d, JsonNodeFactory.instance.arrayNode());
                          var lines = new ArrayList<Map<String, Object>>();
                          BigDecimal total = BigDecimal.ZERO;
                          for (var input : req.items()) {
                            var p =
                                all.stream()
                                    .filter(r -> r.get("sku").equals(input.sku()))
                                    .findFirst()
                                    .orElseThrow(
                                        () ->
                                            new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST, "Unknown SKU"));
                            var line = new LinkedHashMap<>(p);
                            line.put("quantity", input.quantity());
                            var subtotal =
                                ((BigDecimal) p.get("price"))
                                    .multiply(BigDecimal.valueOf(input.quantity()));
                            line.put("subtotal", subtotal);
                            lines.add(line);
                            total = total.add(subtotal);
                          }
                          return Map.<String, Object>of(
                              "items", lines, "amount", total, "currency", "USD");
                        }))
        .doOnNext(
            result ->
                com.tip.ecommerce.product.observability.OperationalLog.write(
                    org.slf4j.LoggerFactory.getLogger(getClass()),
                    org.slf4j.event.Level.INFO,
                    "quote.completed",
                    "request",
                    com.tip.ecommerce.product.observability.OperationalLog.summary(req),
                    "response",
                    com.tip.ecommerce.product.observability.OperationalLog.summary(result)));
  }

  private List<Map<String, Object>> enrich(
      List<Map<String, Object>> rows, JsonNode discounts, JsonNode ratings) {
    return rows.stream()
        .map(
            row -> {
              var p = new LinkedHashMap<String, Object>(row);
              int discount = 0;
              for (var d : discounts)
                if (d.path("sku").asText().equals(row.get("sku")))
                  discount = d.path("percent").asInt();
              BigDecimal original = (BigDecimal) row.get("original_price");
              p.remove("original_price");
              p.put("originalPrice", original);
              p.put("discountPercent", discount);
              p.put(
                  "price",
                  original
                      .multiply(BigDecimal.valueOf(100 - discount))
                      .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
              p.put("image", "/api/products/images/" + row.get("image"));
              p.put("rating", null);
              p.put("reviews", 0);
              for (var r : ratings)
                if (r.path("sku").asText().equals(row.get("sku"))) {
                  p.put("rating", r.path("average").decimalValue());
                  p.put("reviews", r.path("reviews").asInt());
                }
              return (Map<String, Object>) p;
            })
        .toList();
  }

  @GetMapping("/images/{filename}")
  public Mono<ResponseEntity<Resource>> image(@PathVariable String filename) {
    return Mono.fromCallable(
            () -> {
              if (!filename.matches("[A-Z]+-[0-9]+\\.svg"))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
              Path file = images.resolve(filename).normalize();
              if (!file.startsWith(images) || !Files.isRegularFile(file))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
              return ResponseEntity.ok()
                  .contentType(MediaType.valueOf("image/svg+xml"))
                  .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)))
                  .body((Resource) new FileSystemResource(file));
            })
        .subscribeOn(Schedulers.boundedElastic());
  }
}
