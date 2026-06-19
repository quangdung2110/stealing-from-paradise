package com.flashsale.flashsaleservice;

import com.flashsale.flashsaleservice.dto.response.ServerTimeResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FlashsaleServiceApplicationTests {

    @Test
    void mainClassExists() {
        assertNotNull(FlashsaleServiceApplication.class);
    }

    @Test
    void serverTimeResponseBuilder() {
        long now = System.currentTimeMillis();
        ServerTimeResponse response = ServerTimeResponse.builder()
                .serverTime(now)
                .build();
        assertEquals(now, response.getServerTime());
    }
}
