package com.flashsale.chatservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test",
        "dev-data.enabled=false",
        "eureka.client.enabled=false"
})
class ChatServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
