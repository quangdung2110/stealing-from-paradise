package com.flashsale.identityservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.identityservice.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class IdentityEventHandler {

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Transactional
    public void onOrderDelivered(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, new TypeReference<>() {});
            Long sellerId = toLong(event.get("seller_id"));
            if (sellerId == null) {
                log.warn("onOrderDelivered: missing seller_id");
                return;
            }
            userRepository.findById(sellerId).ifPresent(seller -> {
                if ("POSTING_LOCKED".equals(seller.getStatus())) {
                    seller.setStatus("ACTIVE");
                    userRepository.save(seller);
                    log.info("Seller posting unlocked after delivery: sellerId={}, orderId={}",
                            sellerId, event.get("order_id"));
                }
            });
        } catch (Exception e) {
            log.error("Error processing order.delivered in identity-service: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void onSellerOrderCancelled(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, new TypeReference<>() {});
            Long sellerId = toLong(event.get("seller_id"));
            if (sellerId == null) {
                log.warn("onSellerOrderCancelled: missing seller_id");
                return;
            }
            userRepository.findById(sellerId).ifPresent(seller -> {
                if ("ACTIVE".equals(seller.getStatus())) {
                    seller.setStatus("POSTING_LOCKED");
                    userRepository.save(seller);
                    log.info("Seller posting locked after order cancellation: sellerId={}, orderId={}",
                            sellerId, event.get("order_id"));
                }
            });
        } catch (Exception e) {
            log.error("Error processing seller.order_cancelled: {}", e.getMessage(), e);
        }
    }

    public void onOrderCancelled(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, new TypeReference<>() {});
            log.info("Order cancelled audit: orderId={}, userId={}, sellerId={}, cancelledBy={}, reason={}",
                    event.get("order_id"), event.get("user_id"), event.get("seller_id"),
                    event.get("cancelled_by"), event.get("cancel_reason"));
        } catch (Exception e) {
            log.error("Error processing order.cancelled in identity-service: {}", e.getMessage(), e);
        }
    }

    public void onRefundAdminApproved(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, new TypeReference<>() {});
            log.info("Refund approved audit: refundId={}, orderId={}, amount={}, type={}, adminId={}",
                    event.get("refund_id"), event.get("order_id"),
                    event.get("amount"), event.get("type"), event.get("admin_id"));
        } catch (Exception e) {
            log.error("Error processing refund.admin_approved in identity-service: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void onRefundRejected(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, new TypeReference<>() {});
            Long userId = toLong(event.get("user_id"));
            boolean fraudEvidence = Boolean.TRUE.equals(event.get("fraud_evidence"));
            log.info("Refund rejected audit: refundId={}, orderId={}, userId={}, fraudEvidence={}",
                    event.get("refund_id"), event.get("order_id"), userId, fraudEvidence);
            if (fraudEvidence && userId != null && userId > 0) {
                userRepository.findById(userId).ifPresent(user -> {
                    if (!"LOCKED".equals(user.getStatus())) {
                        user.setStatus("LOCKED");
                        userRepository.save(user);
                        log.warn("User account locked due to fraud evidence on refund rejection: userId={}", userId);
                    }
                });
            }
        } catch (Exception e) {
            log.error("Error processing refund.rejected in identity-service: {}", e.getMessage(), e);
        }
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (Exception ignored) {
            return null;
        }
    }
}
