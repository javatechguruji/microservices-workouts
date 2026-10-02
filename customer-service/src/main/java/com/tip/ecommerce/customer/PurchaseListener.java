package com.tip.ecommerce.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PurchaseListener {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public PurchaseListener(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  @KafkaListener(topics = "commerce-order-events", groupId = "customer-preferences-v1")
  @Transactional(rollbackFor = Exception.class)
  public void receive(String body) throws Exception {
    var event = json.readTree(body);
    if (!event.path("status").asText().equals("CONFIRMED")) return;
    if (db.update(
            "INSERT INTO consumed_order VALUES (?) ON CONFLICT DO NOTHING",
            event.path("eventId").asText())
        == 0) return;
    for (var item : event.path("items"))
      db.update(
          "INSERT INTO category_history VALUES (?,?,?,?) ON CONFLICT(tenant,username,category) DO"
              + " UPDATE SET purchases=category_history.purchases+excluded.purchases",
          event.path("tenant").asText(),
          event.path("customerId").asText(),
          item.path("category").asText(),
          item.path("quantity").asInt());
  }
}
