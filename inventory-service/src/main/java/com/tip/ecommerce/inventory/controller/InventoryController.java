package com.tip.ecommerce.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tip.ecommerce.inventory.security.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public InventoryController(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public record Line(String sku, int quantity) {}

  public record Reservation(long orderId, List<Line> items) {}

  @GetMapping
  @RequireAccess(permissions = "inventory:read")
  public List<Map<String, Object>> batch(@RequestParam List<String> skus) {
    if (skus.isEmpty() || skus.size() > 50)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return db.queryForList(
        "SELECT sku,available FROM stock WHERE sku IN ("
            + String.join(",", Collections.nCopies(skus.size(), "?"))
            + ")",
        skus.toArray());
  }

  @GetMapping("/{sku}")
  @RequireAccess(permissions = "inventory:read")
  public Map<String, Object> get(@PathVariable String sku, HttpServletRequest r) {
    var c = Caller.from(r);
    var rows = db.queryForList("SELECT * FROM stock WHERE sku=?", sku);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return Map.of(
        "sku",
        sku,
        "available",
        rows.get(0).get("available"),
        "calledAs",
        c.username(),
        "tenant",
        c.tenant());
  }

  @PostMapping("/reservations")
  @RequireAccess(permissions = "inventory:reserve")
  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> reserve(@RequestBody Reservation req, HttpServletRequest r)
      throws Exception {
    if (req.orderId() < 1
        || req.items() == null
        || req.items().isEmpty()
        || req.items().size() > 50
        || req.items().stream()
            .anyMatch(i -> i == null || i.sku() == null || i.quantity() < 1 || i.quantity() > 99)
        || req.items().stream().map(Line::sku).distinct().count() != req.items().size())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reservation");
    String tenant = Caller.from(r).tenant();
    var sorted = req.items().stream().sorted(Comparator.comparing(Line::sku)).toList();
    String payload = json.writeValueAsString(sorted);
    int inserted =
        db.update(
            "INSERT INTO reservation VALUES (?,?,'RESERVED',?) ON CONFLICT DO NOTHING",
            tenant,
            req.orderId(),
            payload);
    if (inserted == 0) {
      var existing =
          db.queryForMap(
              "SELECT * FROM reservation WHERE tenant=? AND order_id=? FOR UPDATE",
              tenant,
              req.orderId());
      if (!payload.equals(existing.get("items")) || existing.get("state").equals("RELEASED"))
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation mismatch");
      return Map.of("state", existing.get("state"));
    }
    for (var line : sorted)
      if (db.update(
              "UPDATE stock SET available=available-? WHERE sku=? AND available>=?",
              line.quantity(),
              line.sku(),
              line.quantity())
          != 1)
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock: " + line.sku());
    return Map.of("state", "RESERVED");
  }

  @PostMapping("/reservations/{id}/{action}")
  @RequireAccess(permissions = "inventory:commit")
  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> finish(
      @PathVariable long id, @PathVariable String action, HttpServletRequest r) throws Exception {
    if (!List.of("commit", "release").contains(action))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    String tenant = Caller.from(r).tenant();
    var rows =
        db.queryForList(
            "SELECT * FROM reservation WHERE tenant=? AND order_id=? FOR UPDATE", tenant, id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    var row = rows.get(0);
    String target = action.equals("commit") ? "COMMITTED" : "RELEASED";
    String state = (String) row.get("state");
    if (state.equals(target)) return Map.of("state", state);
    if (!state.equals("RESERVED"))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation already finalized");
    if (action.equals("release"))
      for (var line : json.readTree((String) row.get("items")))
        db.update(
            "UPDATE stock SET available=available+? WHERE sku=?",
            line.path("quantity").asInt(),
            line.path("sku").asText());
    db.update("UPDATE reservation SET state=? WHERE tenant=? AND order_id=?", target, tenant, id);
    return Map.of("state", target);
  }
}
