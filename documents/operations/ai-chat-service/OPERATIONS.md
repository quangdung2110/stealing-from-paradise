# AI Chat Service Operations

**Service:** ai-chat-service | **Port:** 8093 | **Database:** MongoDB (fs_chat) + Redis + PageIndex

## Overview

Conversational AI with tool-calling support. Sessions in MongoDB, ephemeral context in Redis. Rate limiting via Redis counters. Confirmation tokens for sensitive operations. PageIndex provides RAG context.

## Key Database Tables

| Table | Purpose |
|---|---|
| `chat_sessions` | User sessions (user_id, title, status, model) |
| `chat_messages` | Messages within a session (role, content, token_count) |
| `pending_confirmations` | User confirmation tokens (token, action_type, expires_at) |
| `tool_call_logs` | Audit log (tool_name, arguments, result, latency_ms) |

## Redis Data Structures

| Key Pattern | Type | Purpose |
|---|---|---|
| `ratelimit:chat:{userId}` | String | Rate limit counter (INCR + EXPIRE) |
| `session:{sessionId}:context` | String | Cached conversation context |
| `confirm:{token}` | String | Pending confirmation payload (JSON) |
| `toolcache:{key}` | String | Cached tool call results |

## Running Locally

```bash
docker-compose up -d ai-chat-service
# Standalone: ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_HOST` | Yes | `localhost` | PostgreSQL host |
| `DB_PORT` | Yes | `5432` | PostgreSQL port |
| `DB_NAME` | Yes | `chat_db` | Database name |
| `DB_USER` | Yes | — | Database username |
| `DB_PASSWORD` | Yes | — | Database password |
| `REDIS_HOST` | Yes | `localhost` | Redis host |
| `REDIS_PORT` | Yes | `6379` | Redis port |
| `OPENAI_API_KEY` | Yes | — | OpenAI API key |
| `OPENAI_MODEL` | No | `gpt-4o` | OpenAI model name |
| `PAGEINDEX_ENDPOINT` | Yes | — | PageIndex RAG endpoint URL |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | `localhost:9092` | Kafka broker |

## Health Check

```
GET /actuator/health
```

PageIndex failure returns DEGRADED (not DOWN) since chat can fall back to non-RAG responses.

## Common Operational Tasks

### View Active Sessions
```sql
SELECT s.id, s.user_id, s.title, COUNT(m.id) AS msgs, MAX(m.created_at) AS last_msg
FROM chat_sessions s LEFT JOIN chat_messages m ON s.id = m.session_id
WHERE s.status = 'ACTIVE' GROUP BY s.id ORDER BY last_msg DESC LIMIT 20;
```

### Clear Expired Confirmations
```sql
DELETE FROM pending_confirmations WHERE expires_at < NOW();
```
```bash
redis-cli KEYS "confirm:*" | while read k; do [ $(redis-cli TTL "$k") -lt 0 ] && redis-cli DEL "$k"; done
```

### Check Rate Limit & PageIndex
```bash
redis-cli GET "ratelimit:chat:<user-id>"            # check counter
redis-cli DEL "ratelimit:chat:<user-id>"            # reset
curl -s "$PAGEINDEX_ENDPOINT/health"                # RAG health
curl -s -X POST "$PAGEINDEX_ENDPOINT/search" \
  -H 'Content-Type: application/json' \
  -d '{"query": "test", "top_k": 3}' | jq '.results | length'
```

### Audit Recent Tool Calls
```sql
SELECT id, session_id, tool_name, latency_ms, created_at
FROM tool_call_logs ORDER BY created_at DESC LIMIT 20;
```

## Troubleshooting

| Symptom | Likely Cause | Check |
|---|---|---|
| Chat returns "rate limited" | User exceeded limit | `redis-cli GET "ratelimit:chat:<id>"`; adjust threshold |
| Responses missing product context | PageIndex unreachable | `curl $PAGEINDEX_ENDPOINT/health` |
| Tool call not executing | Confirmation expired | Check `pending_confirmations.expires_at` |
| High response latency | LLM timeout or large context | Check `tool_call_logs.latency_ms`; trim session context |
| Session context lost | Redis eviction or TTL expired | `redis-cli INFO memory`; increase `maxmemory` |
| OpenAI API errors | Invalid key or quota exceeded | Verify key at platform.openai.com |
