package com.tip.ecommerce.order.service;

import com.fasterxml.jackson.databind.*;
import com.tip.ecommerce.order.client.CommerceGateway;
import com.tip.ecommerce.order.entity.*;
import com.tip.ecommerce.order.observability.OperationalLog;
import com.tip.ecommerce.order.repository.OrderRepository;
import com.tip.ecommerce.order.security.Caller;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckoutService {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CheckoutService.class);
  private final JdbcTemplate db;
  private final OrderRepository orders;
  private final CommerceGateway gateway;
  private final ObjectMapper json;

  public CheckoutService(
      JdbcTemplate db, OrderRepository orders, CommerceGateway gateway, ObjectMapper json) {
    this.db = db;
    this.orders = orders;
    this.gateway = gateway;
    this.json = json;
  }

  public record Line(String sku, int quantity) {}

  public record Request(
      String idempotencyKey,
      List<Line> items,
      String address,
      BigDecimal expectedAmount,
      String customerId) {}

  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> create(Request req, Caller caller) throws Exception {
    String owner = req.customerId() == null ? caller.username() : req.customerId();
    if (!owner.matches("[a-zA-Z0-9@._:-]{1,200}"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid customer username required");
    if (!owner.equals(caller.username()) && !caller.admin())
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only administrators can order for another customer");
    if (!caller.tenant().equals("demo"))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Checkout currently supports demo tenant");
    if (req.idempotencyKey() == null
        || !req.idempotencyKey().matches("[a-zA-Z0-9-]{16,80}")
        || req.address() == null
        || req.address().strip().length() < 10
        || req.address().length() > 500
        || req.items() == null
        || req.items().isEmpty()
        || req.items().size() > 50
        || req.items().stream()
            .anyMatch(
                i ->
                    i == null
                        || i.sku() == null
                        || !i.sku().matches("[A-Z]+-[0-9]+")
                        || i.quantity() < 1
                        || i.quantity() > 99)
        || req.items().stream().map(Line::sku).distinct().count() != req.items().size())
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Valid cart, idempotency key and delivery address required");
    // Serialize identical checkout keys across replicas; retries cannot create duplicate orders.
    db.queryForObject(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
        Object.class,
        caller.tenant() + ":" + owner + ":" + req.idempotencyKey());
    String fingerprint =
        json.writeValueAsString(
            List.of(
                req.items().stream().sorted(Comparator.comparing(Line::sku)).toList(),
                req.address().strip()));
    var existing =
        db.queryForList(
            "SELECT order_id,request_hash FROM checkout WHERE tenant=? AND customer_id=? AND"
                + " idempotency_key=?",
            caller.tenant(),
            owner,
            req.idempotencyKey());
    if (!existing.isEmpty()) {
      if (!existing.get(0).get("request_hash").equals(fingerprint))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Checkout key already used for a different cart");
      OperationalLog.write(
          LOG,
          Level.INFO,
          "checkout.replayed",
          "orderId",
          existing.get(0).get("order_id"),
          "expectedAmount",
          req.expectedAmount());
      return detail(((Number) existing.get(0).get("order_id")).longValue(), caller);
    }
    var profile =
        gateway.get(
            "/api/customers/"
                + org.springframework.web.util.UriUtils.encodePathSegment(
                    owner, java.nio.charset.StandardCharsets.UTF_8)
                + "/preferences");
    if (!profile.path("complete").asBoolean())
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Complete your customer profile before checkout");
    // Never trust browser totals: server obtains current prices and snapshots each line.
    var quote = gateway.post("/api/products/quote", Map.of("items", req.items()));
    if (req.expectedAmount() == null
        || quote.path("amount").decimalValue().compareTo(req.expectedAmount()) != 0)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Prices changed; refresh your cart before paying");
    var order = new Order();
    order.setCustomerId(owner);
    order.setTenant(caller.tenant());
    order.setCreatedAt(Instant.now());
    order.setAmount(quote.path("amount").decimalValue());
    order.setStatus(OrderStatus.PENDING);
    order = orders.saveAndFlush(order);
    for (var item : quote.path("items"))
      db.update(
          "INSERT INTO"
              + " order_item(order_id,sku,name,category,quantity,original_price,unit_price,subtotal)"
              + " VALUES (?,?,?,?,?,?,?,?)",
          order.getId(),
          item.path("sku").asText(),
          item.path("name").asText(),
          item.path("category").asText(),
          item.path("quantity").asInt(),
          item.path("originalPrice").decimalValue(),
          item.path("price").decimalValue(),
          item.path("subtotal").decimalValue());
    db.update(
        "INSERT INTO"
            + " checkout(order_id,tenant,customer_id,idempotency_key,request_hash,state,address)"
            + " VALUES (?,?,?,?,?,'CREATED',?)",
        order.getId(),
        caller.tenant(),
        owner,
        req.idempotencyKey(),
        fingerprint,
        req.address().strip());
    OperationalLog.afterCommit(
        LOG,
        "checkout.accepted",
        "orderId",
        order.getId(),
        "amount",
        order.getAmount(),
        "state",
        "CREATED",
        "items",
        OperationalLog.summary(req.items()));
    return detail(order.getId(), caller);
  }

  public Map<String, Object> detail(long id, Caller caller) {
    var order =
        orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    caller.requireOwner(order.getCustomerId(), order.getTenant(), "orders:read:any");
    var rows =
        db.queryForList("SELECT state,address,error,tracking FROM checkout WHERE order_id=?", id);
    var result = new LinkedHashMap<String, Object>();
    result.put("orderId", id);
    result.put("amount", order.getAmount());
    result.put("status", order.getStatus());
    result.put(
        "items",
        db.queryForList(
            "SELECT sku,name,category,quantity,original_price,unit_price,subtotal FROM order_item"
                + " WHERE order_id=? ORDER BY id",
            id));
    if (!rows.isEmpty()) result.putAll(rows.get(0));
    return result;
  }

  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> ship(long id, String action, Caller caller) throws Exception {
    detail(id, caller);
    var rows = db.queryForList("SELECT state FROM checkout WHERE order_id=? FOR UPDATE", id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Legacy order has no fulfillment");
    String state = (String) rows.get(0).get("state");
    String target = action.equals("ship") ? "SHIPPED" : action.equals("deliver") ? "DELIVERED" : "";
    if (target.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    if (state.equals(target)) return detail(id, caller);
    if (!(state.equals("CONFIRMED") && target.equals("SHIPPED")
        || state.equals("SHIPPED") && target.equals("DELIVERED")))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Order must be confirmed before shipping and shipped before delivery");
    db.update("UPDATE checkout SET state=?,tracking=? WHERE order_id=?", target, "ECOM-" + id, id);
    db.update("UPDATE orders SET status=? WHERE id=?", target, id);
    event(id, target);
    OperationalLog.afterCommit(
        LOG, "checkout.state.changed", "orderId", id, "previousState", state, "state", target);
    var result = detail(id, caller);
    result.put("status", target);
    return result;
  }

  public void event(long id, String status) throws Exception {
    var order = db.queryForMap("SELECT * FROM orders WHERE id=?", id);
    var payload = new LinkedHashMap<String, Object>();
    String eventId = id + ":" + status;
    payload.put("eventId", eventId);
    payload.put("orderId", id);
    payload.put("status", status);
    payload.put("occurredAt", Instant.now().toString());
    payload.put("tenant", order.get("tenant"));
    payload.put("customerId", order.get("customer_id"));
    payload.put(
        "items",
        db.queryForList("SELECT sku,category,quantity FROM order_item WHERE order_id=?", id));
    db.update(
        "INSERT INTO commerce_outbox(event_id,order_id,payload) VALUES (?,?,?) ON CONFLICT DO"
            + " NOTHING",
        eventId,
        id,
        json.writeValueAsString(payload));
  }
}
