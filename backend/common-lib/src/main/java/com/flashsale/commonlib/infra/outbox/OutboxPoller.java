package com.flashsale.commonlib.infra.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Polling relay for services that do NOT use Debezium CDC.
 *
 * <p>Reads {@code NEW} rows from {@code outbox_event}, publishes them to Kafka, and marks them
 * {@code PUBLISHED}. Guarded by {@link SchedulerLock} so only one instance relays at a time
 * (requires {@code flashsale.infra.shedlock.enabled=true} as well).
 *
 * <p>Opt-in via {@code flashsale.infra.outbox.poller.enabled=true}. The CDC services
 * (order/payment/refund) leave this disabled — Debezium relays their outbox instead.
 */
@Component
@ConditionalOnProperty(name = "flashsale.infra.outbox.poller.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String SELECT_SQL = """
            SELECT id, topic, msg_key, payload::text AS payload
            FROM outbox_event
            WHERE status = 'NEW'
            ORDER BY created_at
            LIMIT 100
            """;
    private static final String MARK_SQL =
            "UPDATE outbox_event SET status = 'PUBLISHED', published_at = now() WHERE id = ?";

    @Scheduled(fixedDelayString = "${flashsale.infra.outbox.poll-ms:2000}")
    @SchedulerLock(name = "outbox-poller", lockAtMostFor = "PT1M", lockAtLeastFor = "PT1S")
    public void publishPending() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_SQL);
        if (rows.isEmpty()) return;

        int published = 0;
        for (Map<String, Object> row : rows) {
            UUID id = (UUID) row.get("id");
            String topic = (String) row.get("topic");
            String key = (String) row.get("msg_key");
            String payload = (String) row.get("payload");
            try {
                // Block on the send so the row is only marked PUBLISHED after the broker acks.
                kafkaTemplate.send(topic, key, payload).get();
                jdbcTemplate.update(MARK_SQL, id);
                published++;
            } catch (Exception e) {
                // Leave row as NEW → retried on next poll. Order preserved by created_at.
                log.error("Outbox publish failed id={} topic={}: {}", id, topic, e.getMessage());
                break; // stop this batch to preserve ordering; resume next tick
            }
        }
        if (published > 0) log.debug("Outbox poller published {} events", published);
    }
}
