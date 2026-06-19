package com.flashsale.commonlib.infra.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Distributed locking for {@code @Scheduled} jobs across multiple service instances.
 *
 * <p>Opt-in via {@code flashsale.infra.shedlock.enabled=true} — only services that own a
 * JDBC {@link DataSource} and run scheduled jobs (or the outbox poller) should enable it.
 * The lock state lives in a {@code shedlock} table managed by Flyway in each service schema.
 *
 * <p>Property-gating (instead of {@code @ConditionalOnBean(DataSource.class)}) is deliberate:
 * these configs are component-scanned via {@code scanBasePackages="com.flashsale"}, and
 * {@code @ConditionalOnBean} is unreliable for scanned (non auto-configuration) classes.
 */
@Configuration
@ConditionalOnProperty(name = "flashsale.infra.shedlock.enabled", havingValue = "true")
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime() // use database time to avoid clock skew between instances
                        .build());
    }
}
