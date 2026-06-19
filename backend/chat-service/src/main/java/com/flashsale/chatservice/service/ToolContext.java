package com.flashsale.chatservice.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-local context for passing data between ChatService and tool classes.
 * Tools are called by Spring AI on the same thread, so ThreadLocal is appropriate.
 *
 * <p>Holds:
 * <ul>
 *   <li>Session ID for the current conversation</li>
 *   <li>User ID for the current request</li>
 *   <li>Access token for JWT delegation to other services</li>
 *   <li>Tool event collector for SSE streaming</li>
 *   <li>Level-3 pending flag</li>
 * </ul>
 */
public final class ToolContext {

    private static final ThreadLocal<String> sessionIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<Long> userIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> userEmailHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> userRoleHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> accessTokenHolder = new ThreadLocal<>();
    private static final ThreadLocal<List<ToolEvent>> eventsHolder = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<Boolean> level3PendingHolder = ThreadLocal.withInitial(() -> false);

    private ToolContext() {}

    // ── Session ID ───────────────────────────────────────────────

    public static void setSessionId(String sessionId) {
        sessionIdHolder.set(sessionId);
    }

    public static String getSessionId() {
        return sessionIdHolder.get();
    }

    // ── User ID ──────────────────────────────────────────────────

    public static void setUserId(Long userId) {
        userIdHolder.set(userId);
    }

    public static Long getUserId() {
        return userIdHolder.get();
    }

    public static void setUserEmail(String email) {
        userEmailHolder.set(email);
    }

    public static String getUserEmail() {
        return userEmailHolder.get();
    }

    public static void setUserRole(String role) {
        userRoleHolder.set(role);
    }

    public static String getUserRole() {
        return userRoleHolder.get();
    }

    // ── Access Token ─────────────────────────────────────────────

    public static void setAccessToken(String token) {
        accessTokenHolder.set(token);
    }

    public static String getAccessToken() {
        return accessTokenHolder.get();
    }

    // ── Tool Events ──────────────────────────────────────────────

    public static void addEvent(ToolEvent event) {
        eventsHolder.get().add(event);
    }

    public static List<ToolEvent> getEvents() {
        return eventsHolder.get();
    }

    // ── Level 3 ──────────────────────────────────────────────────

    public static void setLevel3Pending(boolean pending) {
        level3PendingHolder.set(pending);
    }

    public static boolean isLevel3Pending() {
        return level3PendingHolder.get();
    }

    // ── Lifecycle ────────────────────────────────────────────────

    public static void clear() {
        sessionIdHolder.remove();
        userIdHolder.remove();
        userEmailHolder.remove();
        userRoleHolder.remove();
        accessTokenHolder.remove();
        eventsHolder.remove();
        level3PendingHolder.remove();
    }

    // ── Event record ─────────────────────────────────────────────

    public record ToolEvent(String eventType, String toolName, String data) {}
}
