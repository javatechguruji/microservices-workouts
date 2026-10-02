package com.tip.ecommerce.customer;

/** Keep retrying durable events; a failed database write must not be silently skipped. */
@org.springframework.context.annotation.Configuration
public class KafkaRetryConfiguration {
  @org.springframework.context.annotation.Bean
  org.springframework.kafka.listener.DefaultErrorHandler kafkaErrorHandler() {
    return new org.springframework.kafka.listener.DefaultErrorHandler(
        new org.springframework.util.backoff.FixedBackOff(
            5000L, org.springframework.util.backoff.FixedBackOff.UNLIMITED_ATTEMPTS));
  }
}
