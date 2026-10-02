package com.tip.ecommerce.order.controller;

import com.tip.ecommerce.order.client.InventoryGatewayClient;
import com.tip.ecommerce.order.dto.*;
import com.tip.ecommerce.order.security.*;
import com.tip.ecommerce.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@io.swagger.v3.oas.annotations.tags.Tag(name = "order-service")
@RestController
@RequestMapping("/api/orders")
public class OrderController {
  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.jdbc.core.JdbcTemplate db;

  private final OrderService orders;
  private final InventoryGatewayClient inventory;

  public OrderController(OrderService orders, InventoryGatewayClient inventory) {
    this.orders = orders;
    this.inventory = inventory;
  }

  @io.swagger.v3.oas.annotations.Operation(
      operationId = "orderApi1",
      summary = "Create a basic order (legacy lab)",
      description =
          "**Purpose:** Creates a PENDING order with a customer and amount. Does not create"
              + " shopping items or reserve inventory; use checkout for cart purchases.\n\n"
              + "**Access:** orders:create; customer must own the request, or be admin in the same"
              + " tenant.\n\n"
              + "**Typical callers:** Legacy order/payment exercises.\n\n"
              + "**Outbound HTTP / dependencies:** None; order database only.\n\n"
              + "**Asynchronous events:** None.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @io.swagger.v3.oas.annotations.media.Content(
                      mediaType = "application/json",
                      examples =
                          @io.swagger.v3.oas.annotations.media.ExampleObject(
                              value = "{\"customerId\": \"customer1\", \"amount\": 22.0}"))))
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequireAccess(permissions = "orders:create")
  public OrderDto create(@RequestBody CreateOrderRequest request, HttpServletRequest http) {
    Caller c = Caller.from(http);
    if (request.customerId() == null || (!c.admin() && !c.username().equals(request.customerId())))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Customer must be the signed-in owner");
    return orders.createOrder(request, c.tenant());
  }

  @io.swagger.v3.oas.annotations.Operation(
      operationId = "orderApi2",
      summary = "List visible orders",
      description =
          "**Purpose:** Customers see their own orders; privileged callers see orders in their"
              + " tenant.\n\n"
              + "**Access:** orders:read OR orders:read:any; tenant isolation.\n\n"
              + "**Typical callers:** Customer My Orders and administrator order list.\n\n"
              + "**Outbound HTTP / dependencies:** None; order database only.\n\n"
              + "**Asynchronous events:** None.")
  @GetMapping
  @RequireAccess(permissions = {"orders:read", "orders:read:any"})
  public List<OrderDto> all(HttpServletRequest http) {
    Caller c = Caller.from(http);
    return orders.getAllOrders().stream()
        .filter(
            o ->
                c.tenant().equals(o.tenant())
                    && (c.has("orders:read:any") || c.username().equals(o.customerId())))
        .toList();
  }

  @io.swagger.v3.oas.annotations.Operation(
      operationId = "orderApi3",
      summary = "Read order details",
      description =
          "**Purpose:** Returns order identity, customer, amount and status.\n\n"
              + "**Access:** orders:read OR orders:read:any; owner/tenant policy.\n\n"
              + "**Typical callers:** UI, payment-service for authorization/payment checks.\n\n"
              + "**Outbound HTTP / dependencies:** None; order database only.\n\n"
              + "**Asynchronous events:** None.")
  @GetMapping("/{id}")
  @RequireAccess(permissions = {"orders:read", "orders:read:any"})
  public OrderDto get(@PathVariable Long id, HttpServletRequest http) {
    OrderDto order = orders.getOrder(id);
    Caller.from(http).requireOwner(order.customerId(), order.tenant(), "orders:read:any");
    return order;
  }

  @io.swagger.v3.oas.annotations.Operation(
      operationId = "orderApi4",
      summary = "Change a legacy order status",
      description =
          "**Purpose:** For basic lab orders only. Shopping orders return 409 and must use"
              + " fulfillment actions.\n\n"
              + "**Access:** admin AND orders:update; same tenant.\n\n"
              + "**Typical callers:** Admin legacy order editor.\n\n"
              + "**Outbound HTTP / dependencies:** None; order database only.\n\n"
              + "**Asynchronous events:** None.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @io.swagger.v3.oas.annotations.media.Content(
                      mediaType = "application/json",
                      examples =
                          @io.swagger.v3.oas.annotations.media.ExampleObject(
                              value = "{\"status\": \"CONFIRMED\"}"))))
  @PatchMapping("/{id}/status")
  @RequireAccess(permissions = "orders:update", role = "admin")
  public OrderDto update(
      @PathVariable Long id,
      @RequestBody UpdateOrderStatusRequest request,
      HttpServletRequest http) {
    get(id, http); // enforce tenant even for administrators
    if (db != null
        && db.queryForObject("SELECT count(*) FROM checkout WHERE order_id=?", Integer.class, id)
            > 0)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Use fulfillment actions for shopping orders");
    return orders.updateStatus(id, request.status());
  }

  @io.swagger.v3.oas.annotations.Operation(
      operationId = "orderApi5",
      summary = "Inspect trusted caller identity",
      description =
          "**Purpose:** Shows identity reconstructed from headers generated by gateway, including"
              + " roles, permissions and tenant.\n\n"
              + "**Access:** Authenticated caller.\n\n"
              + "**Typical callers:** UI session bootstrap and header-spoofing exercises.\n\n"
              + "**Outbound HTTP / dependencies:** None.\n\n"
              + "**Asynchronous events:** None.")
  @GetMapping("/security/me")
  @RequireAccess
  public Caller me(HttpServletRequest http) {
    return Caller.from(http);
  }

  @io.swagger.v3.oas.annotations.Operation(
      operationId = "orderApi6",
      summary = "Demonstrate admin RBAC",
      description =
          "**Purpose:** Returns a message only when caller has the admin role.\n\n"
              + "**Access:** admin role.\n\n"
              + "**Typical callers:** Security learning exercises.\n\n"
              + "**Outbound HTTP / dependencies:** None.\n\n"
              + "**Asynchronous events:** None.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Example successful response; values are illustrative.",
      content =
          @io.swagger.v3.oas.annotations.media.Content(
              mediaType = "application/json",
              examples =
                  @io.swagger.v3.oas.annotations.media.ExampleObject(
                      value = "{\"message\": \"Admin-only RBAC endpoint\"}")))
  @GetMapping("/security/admin")
  @RequireAccess(role = "admin")
  public Map<String, String> admin() {
    return Map.of("message", "Admin-only RBAC endpoint");
  }

  @io.swagger.v3.oas.annotations.Operation(
      operationId = "orderApi7",
      summary = "Check stock using service credentials",
      description =
          "**Purpose:** First checks the original caller can access this order, then calls"
              + " inventory with order-service credentials. Demonstrates user ABAC before changing"
              + " to machine identity.\n\n"
              + "**Access:** orders:read OR orders:read:any; owner/tenant policy.\n\n"
              + "**Typical callers:** Security learning exercises.\n\n"
              + "**Outbound HTTP / dependencies:** Through gateway: GET /api/inventory/{sku}"
              + " (inventory-service). Keycloak client credentials token is obtained/cached by"
              + " order-service.\n\n"
              + "**Asynchronous events:** None.")
  @GetMapping("/{id}/inventory/{sku}")
  @RequireAccess(permissions = {"orders:read", "orders:read:any"})
  public Map<String, Object> inventory(
      @PathVariable Long id, @PathVariable String sku, HttpServletRequest http) {
    OrderDto order =
        get(id, http); // original user's ABAC is checked before switching to service identity
    return Map.of("orderId", order.id(), "inventory", inventory.get(sku));
  }
}
