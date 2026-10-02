package com.tip.ecommerce.order.messaging;

import com.tip.ecommerce.order.service.CheckoutWorker;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class CommerceScheduler {
  private final CheckoutWorker worker;
  private final JdbcTemplate db;
  private final KafkaTemplate<String, String> kafka;

  public CommerceScheduler(
      CheckoutWorker worker,
      JdbcTemplate db,
      @Value("${spring.kafka.bootstrap-servers}") String brokers) {
    this.worker = worker;
    this.db = db;
    this.kafka =
        new KafkaTemplate<>(
            new DefaultKafkaProducerFactory<>(
                Map.of(
                    "bootstrap.servers",
                    brokers,
                    "key.serializer",
                    "org.apache.kafka.common.serialization.StringSerializer",
                    "value.serializer",
                    "org.apache.kafka.common.serialization.StringSerializer",
                    "enable.idempotence",
                    true,
                    "acks",
                    "all",
                    "max.block.ms",
                    3000,
                    "request.timeout.ms",
                    3000,
                    "delivery.timeout.ms",
                    10000)));
  }

  @Scheduled(fixedDelay = 500)
  public void work() {
    try {
      worker.step();
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn("Checkout step will retry: {}", e.getClass().getSimpleName());
    }
  }

  @Scheduled(fixedDelay = 1500)
  public void publish() {
    for (var row :
        db.queryForList(
            "SELECT * FROM commerce_outbox WHERE NOT published ORDER BY order_id, CASE"
                + " split_part(event_id,':',2) WHEN 'CONFIRMED' THEN 1 WHEN 'SHIPPED' THEN 2 WHEN"
                + " 'DELIVERED' THEN 3 ELSE 0 END LIMIT 20")) {
      try {
        kafka
            .send(
                "commerce-order-events",
                row.get("order_id").toString(),
                (String) row.get("payload"))
            .get(12, java.util.concurrent.TimeUnit.SECONDS);
        db.update(
            "UPDATE commerce_outbox SET published=true WHERE event_id=?", row.get("event_id"));
      } catch (Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        break;
      }
    }
  }

  @jakarta.annotation.PreDestroy
  public void close() {
    kafka.destroy();
  }
}
