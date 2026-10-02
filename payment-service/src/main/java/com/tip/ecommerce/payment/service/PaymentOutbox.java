package com.tip.ecommerce.payment.service;

import com.tip.ecommerce.payment.event.PaymentCompletedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class PaymentOutbox {
  private final JdbcTemplate db;
  private final KafkaTemplate<String, PaymentCompletedEvent> kafka;

  public PaymentOutbox(JdbcTemplate db, KafkaTemplate<String, PaymentCompletedEvent> kafka) {
    this.db = db;
    this.kafka = kafka;
  }

  @Scheduled(fixedDelay = 1500)
  public void publish() {
    for (var row :
        db.queryForList(
            "SELECT * FROM payment_outbox WHERE NOT published ORDER BY order_id LIMIT 20")) {
      long id = ((Number) row.get("order_id")).longValue();
      try {
        kafka
            .send(
                "payment-completed",
                String.valueOf(id),
                new PaymentCompletedEvent(
                    id, ((Number) row.get("payment_id")).longValue(), "SUCCESS"))
            .get(10, java.util.concurrent.TimeUnit.SECONDS);
        db.update("UPDATE payment_outbox SET published=true WHERE order_id=?", id);
      } catch (Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        break;
      }
    }
  }
}
