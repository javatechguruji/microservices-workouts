package com.tip.ecommerce.order.service;

import com.tip.ecommerce.order.client.CommerceGateway;
import com.tip.ecommerce.order.observability.OperationalLog;
import java.util.*;
import org.slf4j.event.Level;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckoutWorker {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CheckoutWorker.class);
  private final io.micrometer.core.instrument.MeterRegistry metrics;
  private final JdbcTemplate db;
  private final CommerceGateway gateway;
  private final CheckoutService checkout;

  public CheckoutWorker(
      JdbcTemplate db,
      CommerceGateway gateway,
      CheckoutService checkout,
      io.micrometer.core.instrument.MeterRegistry metrics) {
    this.metrics = metrics;
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
    var amount =
        db.queryForObject("SELECT amount FROM orders WHERE id=?", java.math.BigDecimal.class, id);
    int attempt = ((Number) row.get("attempts")).intValue() + 1;
    String dependency = state.equals("RESERVED") ? "payment-service" : "inventory-service";
    OperationalLog.write(
        LOG,
        Level.DEBUG,
        "checkout.step.started",
        "orderId",
        id,
        "amount",
        amount,
        "state",
        state,
        "attempt",
        attempt,
        "dependency",
        dependency);
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
      OperationalLog.afterCommit(
          LOG,
          "checkout.state.changed",
          "orderId",
          id,
          "amount",
          amount,
          "previousState",
          state,
          "state",
          next,
          "attempt",
          attempt);
    } catch (ResponseStatusException e) {
      metrics.counter("commerce.checkout.attempt.failures", "state", state).increment();
      boolean rejected =
          e.getStatusCode().value() == 409 && (state.equals("CREATED") || state.equals("RESERVED"));
      OperationalLog.write(
          LOG,
          Level.WARN,
          "checkout.dependency.failed",
          "orderId",
          id,
          "amount",
          amount,
          "state",
          state,
          "dependency",
          dependency,
          "status",
          e.getStatusCode().value(),
          "attempt",
          attempt,
          "retryInSeconds",
          rejected ? 0 : 10,
          "nextAction",
          rejected ? (state.equals("CREATED") ? "FAIL_ORDER" : "RELEASE_STOCK") : "RETRY",
          "failure",
          OperationalLog.failure(e));
      if (state.equals("CREATED") && e.getStatusCode().value() == 409) {
        db.update(
            "UPDATE checkout SET state='FAILED',error='One or more items are out of stock' WHERE"
                + " order_id=?",
            id);
        db.update("UPDATE orders SET status='FAILED' WHERE id=?", id);
        checkout.event(id, "FAILED");
        OperationalLog.afterCommit(
            LOG,
            "checkout.state.changed",
            "orderId",
            id,
            "amount",
            amount,
            "previousState",
            state,
            "state",
            "FAILED",
            "reason",
            "STOCK_REJECTED");
      } else if (state.equals("RESERVED") && e.getStatusCode().value() == 409) {
        db.update(
            "UPDATE checkout SET state='RELEASING',error='Payment rejected; releasing stock' WHERE"
                + " order_id=?",
            id);
        OperationalLog.afterCommit(
            LOG,
            "checkout.state.changed",
            "orderId",
            id,
            "amount",
            amount,
            "previousState",
            state,
            "state",
            "RELEASING",
            "reason",
            "PAYMENT_REJECTED");
      } else
        db.update(
            "UPDATE checkout SET attempts=attempts+1,error='Waiting for a dependency; retrying"
                + " safely',next_attempt=now()+interval '10 seconds' WHERE order_id=?",
            id);
    }
  }
}
