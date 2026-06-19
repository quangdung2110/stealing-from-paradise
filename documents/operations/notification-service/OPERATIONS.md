# Notification Service Operations

**Service:** notification-service | **Port:** 8092 | **Database:** MongoDB (notification_db)

## Overview

Event-driven notification pipeline: consumes Kafka events, persists to MongoDB, emits live notifications to active per-user SSE sinks, and replays missed notifications from MongoDB.

## Architecture

```
Kafka (events) -> Consumer -> MongoDB (persistence) -> per-user SSE sink -> SSE clients
```

## Key MongoDB Collections

| Collection | Purpose |
|---|---|
| `mg_notifications` | All notification documents (recipient, type, payload, read status) |

Document schema fields: `_id`, `userId`, `type`, `title`, `message`, `payload`, `read` (boolean), `createdAt`.

## Running Locally

```bash
# Via docker-compose (recommended)
docker-compose up -d notification-service

# Standalone (requires MongoDB + Kafka)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `MONGODB_URI` | Yes | `mongodb://localhost:27017` | MongoDB connection string |
| `MONGODB_DATABASE` | Yes | `notification_db` | MongoDB database name |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | `localhost:9092` | Kafka broker |
| `REDIS_HOST` | No | `localhost` | Reserved for future multi-instance fan-out; not required for current SSE replay path |
| `REDIS_PORT` | No | `6379` | Reserved for future multi-instance fan-out |

## Health Check

```
GET /actuator/health
```

Healthy response includes MongoDB and Kafka consumer components.

## Logging

SLF4J to stdout. Key loggers:
```
logging.level.com.paradise.notification=DEBUG
logging.level.org.springframework.data.mongodb=INFO
```

## Common Operational Tasks

### Clear Old Notifications
```javascript
// In mongosh — delete notifications older than 90 days
db.mg_notifications.deleteMany({
  createdAt: { $lt: new Date(Date.now() - 90 * 24 * 60 * 60 * 1000) }
});
```

### Check SSE Connections
```bash
# Count active SSE connections (application-level metric)
curl -s http://localhost:8092/actuator/metrics/sse.connections.active | jq .
```

### Verify Kafka Consumer Lag
```bash
# Using kafka-consumer-groups
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group notification-service \
  --describe
```

### Query Notifications for a User
```javascript
db.mg_notifications.find({ userId: "<user-id>" })
  .sort({ createdAt: -1 })
  .limit(50)
  .toArray();
```

### Mark All Notifications Read for a User
```javascript
db.mg_notifications.updateMany(
  { userId: "<user-id>", read: false },
  { $set: { read: true } }
);
```

## Troubleshooting

| Symptom | Likely Cause | Check |
|---|---|---|
| SSE stream not receiving | No active sink, auth header missing, or Kafka consumer lag | Check `X-User-Id`, consumer logs, and `sse.connections.active` metric |
| Notifications not persisted | Kafka consumer lag or MongoDB down | Check consumer group lag; verify MongoDB connection |
| High memory usage | Too many open SSE connections | Check `sse.connections.active` metric; add connection timeout |
| Duplicate notifications | Kafka consumer rebalance or no idempotency | Check consumer group stability; verify idempotency key handling |
| MongoDB growing unbounded | No TTL index on `createdAt` | Create TTL index: `db.mg_notifications.createIndex({ createdAt: 1 }, { expireAfterSeconds: 7776000 })` |
