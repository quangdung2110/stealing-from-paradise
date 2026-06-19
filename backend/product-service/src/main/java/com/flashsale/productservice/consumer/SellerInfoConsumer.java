package com.flashsale.productservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes seller identity events from Kafka and denormalizes the seller's
 * display name into the {@code products.seller_name} column.
 *
 * <p>Events handled:
 * <ul>
 *   <li>{@link KafkaTopics#SELLER_REGISTERED} — fired when a buyer registers
 *       as a seller (carries initial {@code full_name}).</li>
 *   <li>{@link KafkaTopics#ACCOUNT_UPDATED} — fired when the user updates
 *       their profile (carries new {@code full_name}).</li>
 * </ul>
 *
 * <p>The product-service does not depend on identity-service over HTTP: it
 * relies on these events (plus the current implementation of {@code UserDetailsImpl}
 * which exposes {@code username} but not {@code fullName}) to keep
 * {@code products.seller_name} consistent. Both payloads contain
 * {@code user_id} (or {@code userId}) and {@code full_name} (or {@code fullName}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SellerInfoConsumer {

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {
                    KafkaTopics.SELLER_REGISTERED,
                    KafkaTopics.ACCOUNT_UPDATED
            },
            groupId = "product-service-seller-info"
    )
    @Transactional
    public void onSellerInfoChanged(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            Long userId = readLong(root, "user_id", "userId");
            String fullName = readText(root, "full_name", "fullName");

            if (userId == null) {
                log.warn("Seller-info event missing user_id: {}", message);
                return;
            }

            if (fullName == null || fullName.isBlank()) {
                log.debug("Seller-info event for userId={} has no full_name — skipping", userId);
                return;
            }

            int updated = productRepository.updateSellerNameForAllProducts(userId, fullName);
            if (updated > 0) {
                log.info("Synced seller_name='{}' for userId={} ({} products updated)", fullName, userId, updated);
            } else {
                log.debug("No active products for seller userId={} (nothing to update)", userId);
            }
        } catch (Exception e) {
            // Swallow: DLT would just thrash for malformed events. Log and move on.
            log.error("Failed to process seller-info event: {}", message, e);
        }
    }

    private Long readLong(JsonNode root, String... keys) {
        for (String k : keys) {
            JsonNode n = root.get(k);
            if (n != null && !n.isNull()) {
                try {
                    return n.asLong();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private String readText(JsonNode root, String... keys) {
        for (String k : keys) {
            JsonNode n = root.get(k);
            if (n != null && !n.isNull() && !n.asText().isBlank()) {
                return n.asText();
            }
        }
        return null;
    }
}
