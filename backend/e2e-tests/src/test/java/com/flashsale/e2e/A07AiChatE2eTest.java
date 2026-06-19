package com.flashsale.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for AI chat service workflows.
 * Covers UC-AICHAT-001, UC-AICHAT-002, UC-AICHAT-003.
 */
@DisplayName("E2E-A07: AI chat flows")
class A07AiChatE2eTest extends E2eSupport {

    @Test
    @DisplayName("start chat session → get suggestions → list sessions → send chat message → close session")
    void chatSessionLifecycle() {
        String token = login(BUYER);

        // 1. Start a new session
        HttpResponse<String> createResp = post("/api/ai/sessions", token, Map.of());
        assertEquals(201, createResp.statusCode(), createResp.body());
        JsonNode sessionData = json(createResp).get("data");
        assertNotNull(sessionData, createResp.body());
        String sessionId = text(sessionData, "id");
        assertNotNull(sessionId);

        // 2. Get AI suggest prompts
        HttpResponse<String> suggestResp = get("/api/ai/suggest", token);
        assertEquals(200, suggestResp.statusCode(), suggestResp.body());
        JsonNode suggestData = json(suggestResp).get("data");
        assertNotNull(suggestData);
        assertTrue(suggestData.isArray());

        // 3. List active sessions
        HttpResponse<String> listResp = get("/api/ai/sessions", token);
        assertEquals(200, listResp.statusCode(), listResp.body());
        JsonNode sessions = json(listResp).get("data");
        assertNotNull(sessions);
        assertTrue(sessions.isArray() && !sessions.isEmpty());

        // 4. Send message (produces text/event-stream; must Accept that explicitly)
        HttpResponse<String> chatResp = postWithAccept("/api/ai/chat", token,
                "text/event-stream", Map.of(
                        "sessionId", sessionId,
                        "message", "Hello, assistant! Tell me a joke."));
        assertEquals(200, chatResp.statusCode(), chatResp.body());
        assertTrue(chatResp.headers().firstValue("Content-Type").orElse("").contains("text/event-stream"),
                "expected text/event-stream content type, got: " + chatResp.headers().firstValue("Content-Type").orElse(""));

        // 5. Close session
        HttpResponse<String> closeResp = delete("/api/ai/sessions/" + sessionId, token);
        assertEquals(200, closeResp.statusCode(), closeResp.body());
    }
}
