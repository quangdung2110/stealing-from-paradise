package com.flashsale.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Authentication through the gateway: login happy path, credential rejection,
 * and JWT enforcement on protected routes.
 */
@DisplayName("E2E-A02: authentication")
class A02AuthE2eTest extends E2eSupport {

    @Test
    @DisplayName("seeded buyer can log in and gets a JWT")
    void buyerLogin() {
        String token = login(BUYER);
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length, "accessToken should be a JWT");
    }

    @Test
    @DisplayName("wrong password is rejected")
    void wrongPasswordRejected() {
        HttpResponse<String> resp = post("/api/v1/auth/login", null,
                Map.of("credential", BUYER, "password", "definitely-wrong"));
        assertTrue(resp.statusCode() >= 400 && resp.statusCode() < 500,
                "expected 4xx for bad credentials, got " + resp.statusCode() + ": " + resp.body());
    }

    @Test
    @DisplayName("protected endpoint without token is rejected")
    void protectedEndpointRequiresJwt() {
        HttpResponse<String> resp = get("/api/v1/orders?page=0&size=5", null);
        assertTrue(resp.statusCode() == 401 || resp.statusCode() == 403,
                "expected 401/403 without JWT, got " + resp.statusCode() + ": " + resp.body());
    }

    @Test
    @DisplayName("buyer token grants access to own orders")
    void buyerCanListOwnOrders() {
        HttpResponse<String> resp = get("/api/v1/orders?page=0&size=5", login(BUYER));
        assertEquals(200, resp.statusCode(), resp.body());
    }
}
