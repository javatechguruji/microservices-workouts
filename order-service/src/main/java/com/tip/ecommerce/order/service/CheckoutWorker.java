package com.tip.ecommerce.order.service;

import com.tip.ecommerce.order.client.CommerceGateway;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckoutWorker {
  private final JdbcTemplate db;
  private final CommerceGateway gateway;
  private final CheckoutService checkout;

  public CheckoutWorker(JdbcTemplate db, CommerceGateway gateway, CheckoutService checkout) {
    this.db = db;
    this.gateway = gateway;
    this.checkout = checkout;
  }

  @Transactional(rollbackFor = Exception.class)
  public void step() throws Exception {
    var rows =
        db.queryForList(
            "SELECT * FROM checkout WHERE state IN ('CREATED','RESERVED','PAID','RELEASING') AND"
                + " next_attempt<=now() ORDER BY order_id LIMIT 1 FOR UPDATE SKIP LOCKED");
    if (rows.isEmpty()) return;
    var row = rows.get(0);
    long id = ((Number) row.get("order_id")).longValue();
    String state = (String) row.get("state");
    try {
      String next;
      if (state.equals("CREATED")) {
        var items = db.queryForList("SELECT sku,quantity FROM order_item WHERE order_id=?", id);
        gateway.post("/api/inventory/reservations", Map.of("orderId", id, "items", items));
        next = "RESERVED";
      } else if (state.equals("RESERVED")) {
        gateway.post(
            "/payments",
            Map.of(
                "orderId",
                id,
                "amount",
                db.queryForObject(
                    "SELECT amount FROM orders WHERE id=?", java.math.BigDecimal.class, id)));
        next = "PAID";
      } else if (state.equals("RELEASING")) {
        gateway.post("/api/inventory/reservations/" + id + "/release", Map.of());
        next = "FAILED";
        db.update("UPDATE orders SET status='FAILED' WHERE id=?", id);
        checkout.event(id, next);
      } else {
        gateway.post("/api/inventory/reservations/" + id + "/commit", Map.of());
        next = "CONFIRMED";
        db.update("UPDATE orders SET status='CONFIRMED' WHERE id=?", id);
        checkout.event(id, next);
      }
      db.update(
          "UPDATE checkout SET state=?,error=NULL,attempts=0,next_attempt=now() WHERE order_id=?",
          next,
          id);
    } catch (ResponseStatusException e) {
      if (state.equals("CREATED") && e.getStatusCode().value() == 409) {
        db.update(
            "UPDATE checkout SET state='FAILED',error='One or more items are out of stock' WHERE"
                + " order_id=?",
            id);
        db.update("UPDATE orders SET status='FAILED' WHERE id=?", id);
        checkout.event(id, "FAILED");
      } else if (state.equals("RESERVED") && e.getStatusCode().value() == 409)
        db.update(
            "UPDATE checkout SET state='RELEASING',error='Payment rejected; releasing stock' WHERE"
                + " order_id=?",
            id);
      else
        db.update(
            "UPDATE checkout SET attempts=attempts+1,error='Waiting for a dependency; retrying"
                + " safely',next_attempt=now()+interval '10 seconds' WHERE order_id=?",
            id);
    }
  }
}
