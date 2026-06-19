package com.flashsale.devdata;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Dev-only standalone runner that can be used to reset and reseed ALL
 * dev databases (payment, order, product) from a single command.
 *
 * Usage:
 *   cd backend/dev-data-runner
 *   mvn spring-boot:run -Dspring-boot.run.profiles=dev
 *
 * Or set environment:
 *   SPRING_PROFILES_ACTIVE=dev
 *
 * For selective reset, set dev-data.reset-{service}=true.
 */
@SpringBootApplication
@Slf4j
public class DevDataRunner implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(DevDataRunner.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("=================================================");
        log.info("  DevDataRunner — Dev Environment Data Seeder");
        log.info("  Services seeded (when SPRING_PROFILES_ACTIVE=dev):");
        log.info("    1. identity-service     — users / roles / addresses");
        log.info("    2. product-service      — categories / products / variants");
        log.info("    3. order-service        — parent orders / sub-orders / items");
        log.info("    4. payment-service      — stripe accts / transactions / transfers");
        log.info("    5. flashsale-service    — sessions / items / reminders");
        log.info("    6. refund-service       — refunds / refund items");
        log.info("    7. notification-service — notifications (Mongo)");
        log.info("    8. chat-service         — chat sessions / messages (Mongo)");
        log.info("    9. (Bonus) Each service also seeds its JSON test-dataset");
        log.info("       from test-data/*.json in classpath:");
        log.info("");
        log.info("To seed: each service auto-seeds on first start with profile=dev.");
        log.info("To wipe + reseed: set dev-data.reset=true in the service's");
        log.info("application-dev.yml (or pass DEV_DATA_RESET=true env var).");
        log.info("");
    }
}
