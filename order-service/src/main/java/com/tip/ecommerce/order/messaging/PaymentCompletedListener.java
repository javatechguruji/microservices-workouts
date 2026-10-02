package com.tip.ecommerce.order.messaging;

import com.tip.ecommerce.order.entity.OrderStatus;
import com.tip.ecommerce.order.event.PaymentCompletedEvent;
import com.tip.ecommerce.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class PaymentCompletedListener {

  private static final Logger log = LoggerFactory.getLogger(PaymentCompletedListener.class);

  private final OrderService orderService;

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.jdbc.core.JdbcTemplate db;

  public PaymentCompletedListener(OrderService orderService) {
    this.orderService = orderService;
  }

  // No dedup-by-message-id table here (contrast docs/kafka-notes.md C1):
  // updateStatus() is naturally idempotent — re-applying the same status
  // to an already-updated order is a harmless no-op, so a redelivered
  // message after a crash-before-commit needs no special handling.
  @KafkaListener(topics = "payment-completed", groupId = "order-group")
  public void handlePaymentCompleted(PaymentCompletedEvent event, Acknowledgment ack) {
    if (db.queryForObject(
            "SELECT count(*) FROM checkout WHERE order_id=?", Integer.class, event.orderId())
        > 0) {
      ack.acknowledge();
      return;
    }
    if (orderService.getOrder(event.orderId()).status() != OrderStatus.PENDING) {
      ack.acknowledge();
      return;
    }
    OrderStatus newStatus =
        "SUCCESS".equals(event.status()) ? OrderStatus.CONFIRMED : OrderStatus.FAILED;
    orderService.updateStatus(event.orderId(), newStatus);
    log.info("Order {} moved to {} (payment {})", event.orderId(), newStatus, event.paymentId());
    ack.acknowledge();
  }
}
