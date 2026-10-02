package com.tip.ecommerce.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class CommerceNotifications {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public CommerceNotifications(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  @KafkaListener(
      topics = "commerce-order-events",
      groupId = "commerce-notifications-v1",
      properties = {"value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"})
  public void receive(String payload, Acknowledgment ack) throws Exception {
    var e = json.readTree(payload);
    db.update(
        "INSERT INTO notification_inbox(event_id,tenant,customer_id,order_id,status,created_at)"
            + " VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
        e.path("eventId").asText(),
        e.path("tenant").asText(),
        e.path("customerId").asText(),
        e.path("orderId").asLong(),
        e.path("status").asText(),
        java.sql.Timestamp.from(
            e.hasNonNull("occurredAt")
                ? java.time.Instant.parse(e.path("occurredAt").asText())
                : java.time.Instant.now()));
    ack.acknowledge();
  }
}
