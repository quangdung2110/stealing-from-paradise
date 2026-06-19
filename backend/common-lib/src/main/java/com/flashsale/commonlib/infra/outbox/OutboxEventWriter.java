package com.flashsale.commonlib.infra.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Transactional Outbox writer (shared).
 *
 * <p>Call {@link #append} <b>inside the same {@code @Transactional}</b> as the business write so the
 * outbox row commits atomically with the business data — eliminating the dual-write problem of
 * {@code repository.save()} followed by a separate {@code kafkaTemplate.send()}.
 *
 * <p>The row is later relayed to Kafka by either:
 * <ul>
 *   <li>Debezium CDC (order/payment/refund) reading the WAL via the Outbox Event Router SMT, or</li>
 *   <li>{@link OutboxPoller} (other services).</li>
 * </ul>
 *
 * <p>Uses {@link JdbcTemplate} (not JPA) so it stays decoupled from each service's entity scan while
 * still joining the active Spring transaction on the shared {@link javax.sql.DataSource}.
 *
 * <p>Opt-in via {@code flashsale.infra.outbox.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "flashsale.infra.outbox.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final String INSERT_SQL = """
            INSERT INTO outbox_event
                (id, aggregate_type, aggregate_id, type, topic, msg_key, payload, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'NEW', now())
            """;

    /**
     * Append an event destined for {@code topic} with the given Kafka message {@code key}.
     * {@code payload} is serialized to JSON (a {@link String} payload is assumed to be JSON already).
     */
    public void append(String topic, String key, Object payload) {
        append(topic, key, topic, topic, key, payload);
    }

    /**
     * Full form exposing Debezium Outbox SMT routing fields.
     *
     * @param aggregateType logical aggregate type (e.g. {@code "order"})
     * @param aggregateId   aggregate identifier
     * @param type          event type
     * @param topic         destination Kafka topic (SMT routes by this column)
     * @param key           Kafka message key
     * @param payload       event body (Object → JSON, or a JSON String passed through)
     */
    public void append(String aggregateType, String aggregateId, String type,
                       String topic, String key, Object payload) {
        try {
            String json = (payload instanceof String s) ? s : objectMapper.writeValueAsString(payload);
            jdbcTemplate.update(INSERT_SQL,
                    UUID.randomUUID(), aggregateType, aggregateId, type, topic, key, json);
        } catch (Exception e) {
            // Propagate so the surrounding business transaction rolls back — never silently drop.
            throw new IllegalStateException("Failed to append outbox event for topic " + topic, e);
        }
    }
}
