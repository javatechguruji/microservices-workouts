package com.tip.ecommerce.order.controller;

import com.tip.ecommerce.order.security.*;
import com.tip.ecommerce.order.service.CheckoutService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "order-service")
@RestController
@RequestMapping("/api/orders")
public class CheckoutController {
  private final CheckoutService checkout;

  public CheckoutController(CheckoutService checkout) {
    this.checkout = checkout;
  }

  @io.swagger.v3.oas.annotations.Operation(
      operationId = "orderApi8",
      summary = "Accept an idempotent shopping checkout",
      description =
          "**Purpose:** Validates profile and current quote, saves order/items and durable CREATED"
              + " workflow, then returns 202. Acceptance is not confirmation or payment. Same"
              + " tenant/customer/key and same items/address returns the same order; changed"
              + " contents or stale total can return 409. customerId is optional for shoppers and"
              + " supports admin-assisted purchase.\n\n"
              + "**Access:** orders:create; customer owner or admin, same tenant.\n\n"
              + "**Typical callers:** React cart and admin Create Order page.\n\n"
              + "**Outbound HTTP / dependencies:** Before 202, through gateway: GET"
              + " /api/customers/{username}/preferences (customer-service), POST"
              + " /api/products/quote (PGS). After acceptance, worker calls POST"
              + " /api/inventory/reservations, POST /payments, then POST"
              + " /api/inventory/reservations/{id}/commit; on payment rejection it calls release."
              + " Inventory/payment are not called by the HTTP controller before returning 202.\n\n"
              + "**Asynchronous events:** Worker writes commerce-order-events to an outbox on"
              + " CONFIRMED/FAILED; notification/customer consume asynchronously. Payment emits"
              + " payment-completed through its own outbox.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @io.swagger.v3.oas.annotations.media.Content(
                      mediaType = "application/json",
                      examples =
                          @io.swagger.v3.oas.annotations.media.ExampleObject(
                              value =
                                  "{\"idempotencyKey\": \"demo-checkout-00000001\", \"items\":"
                                      + " [{\"sku\": \"BOOK-1\", \"quantity\": 1}], \"address\":"
                                      + " \"123 Learning Lane, Chicago IL 60601\","
                                      + " \"expectedAmount\": 22.0}"))))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "202",
      description = "Example successful response; values are illustrative.",
      content =
          @io.swagger.v3.oas.annotations.media.Content(
              mediaType = "application/json",
              examples =
                  @io.swagger.v3.oas.annotations.media.ExampleObject(
                      value =
                          "{\"orderId\": 41, \"amount\": 22.0, \"status\": \"PENDING\", \"state\":"
                              + " \"CREATED\", \"address\": \"123 Learning Lane, Chicago IL"
                              + " 60601\", \"error\": null, \"tracking\": null, \"items\":"
                              + " [{\"sku\": \"BOOK-1\", \"name\": \"Example book\", \"category\":"
                              + " \"Books\", \"quantity\": 1, \"original_price\": 25.0,"
                              + " \"unit_price\": 22.0, \"subtotal\": 22.0}]}")))
  @PostMapping("/checkout")
  @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
  @RequireAccess(permissions = "orders:create")
  public Map<String, Object> create(@RequestBody CheckoutService.Request body, HttpServletRequest r)
      throws Exception {
    return checkout.create(body, Caller.from(r));
  }

  @io.swagger.v3.oas.annotations.Operation(
      operationId = "orderApi9",
      summary = "Read checkout and fulfillment progress",
      description =
          "**Purpose:** Returns saved state, item price snapshots, delivery address, retry error"
              + " and tracking. UI intentionally checks every two seconds while"
              + " CREATED/RESERVED/PAID/RELEASING. This read never retries payment itself. Legacy"
              + " orders can have an empty items list and no checkout state.\n\n"
              + "**Access:** orders:read OR orders:read:any; owner/tenant policy.\n\n"
              + "**Typical callers:** Customer/admin order details.\n\n"
              + "**Outbound HTTP / dependencies:** None; reads saved order/checkout/items.\n\n"
              + "**Asynchronous events:** None.")
  @GetMapping("/{id}/fulfillment")
  @RequireAccess(permissions = {"orders:read", "orders:read:any"})
  public Map<String, Object> detail(@PathVariable long id, HttpServletRequest r) {
    return checkout.detail(id, Caller.from(r));
  }

  @io.swagger.v3.oas.annotations.Operation(
      operationId = "orderApi10",
      summary = "Ship or deliver a shopping order",
      description =
          "**Purpose:** action must be ship or deliver. CONFIRMED -> SHIPPED -> DELIVERED; repeated"
              + " current action is safe, invalid order of transitions returns 409. Tracking is"
              + " simulated, not a carrier integration.\n\n"
              + "**Access:** admin AND orders:update; same tenant.\n\n"
              + "**Typical callers:** Admin fulfillment controls.\n\n"
              + "**Outbound HTTP / dependencies:** None; database and transactional outbox.\n\n"
              + "**Asynchronous events:** Publishes SHIPPED/DELIVERED commerce-order-events"
              + " asynchronously to notification/customer consumers.")
  @PostMapping("/{id}/fulfillment/{action}")
  @RequireAccess(role = "admin", permissions = "orders:update")
  public Map<String, Object> advance(
      @PathVariable long id, @PathVariable String action, HttpServletRequest r) throws Exception {
    return checkout.ship(id, action, Caller.from(r));
  }
}
