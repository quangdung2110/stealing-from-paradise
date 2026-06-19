package com.flashsale.flashsaleservice.config;

import io.r2dbc.spi.ConnectionFactory;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.r2dbc.R2dbcLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ShedLock for flashsale-service.
 *
 * <p>Defined locally (not in common-lib) because this service's main class does not set
 * {@code scanBasePackages="com.flashsale"}, and because it is R2DBC-only — so it uses the
 * reactive {@link R2dbcLockProvider} over the shared {@code shedlock} table (Flyway-managed)
 * rather than the JDBC provider in {@code common-lib}.
 *
 * <p>Note: ShedLock releases the lock when the annotated method returns, so the scheduled
 * methods block on their reactive pipelines ({@code blockLast()}) to keep the lock held for
 * the duration of the actual work.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class FlashSaleShedLockConfig {

    @Bean
    public LockProvider lockProvider(ConnectionFactory connectionFactory) {
        return new R2dbcLockProvider(connectionFactory);
    }
}
