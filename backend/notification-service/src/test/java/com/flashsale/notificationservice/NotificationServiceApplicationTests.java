package com.flashsale.notificationservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "dev-data.enabled=false",
        "eureka.client.enabled=false"
})
class NotificationServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
