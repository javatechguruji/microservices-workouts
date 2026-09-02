# notification-service

Notification microservice — part of the [microservices-workouts](../README.md) e-commerce system. Runs on port 9103.

No database — it has nothing worth persisting yet. It's purely a Kafka consumer (see `docs/kafka-notes.md`); `POST /notifications` is a manual stand-in for the `@KafkaListener` you'll wire on the `payment-completed` topic, so the "send" logic can be tested before Kafka exists.

## Run locally
```bash
./mvnw spring-boot:run
```

## Endpoints
- `POST /notifications` — `{"orderId": 1, "status": "SUCCESS"}` (logs it; see `NotificationService.send`)
