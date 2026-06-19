package com.flashsale.orderservice.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.SimpleDeadlineManager;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.jpa.JpaSagaStore;
import org.axonframework.serialization.Serializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Axon Framework configuration for the Order Domain service.
 *
 * <p>Explicitly defines {@link TokenStore} and {@link SagaStore} beans so Axon
 * uses the Flyway-managed table schemas (BYTEA columns, VARCHAR timestamps) instead
 * of relying on Hibernate auto-DDL to generate its own entity definitions.
 *
 * <p>Key decisions:
 * <ul>
 *   <li>{@link JpaTokenStore}: serializes tracking tokens using the injected XStream
 *       serializer. The resulting byte[] is bound directly as a BYTEA parameter via
 *       JDBC. Combined with the
 *       {@link com.flashsale.commonlib.config.ByteaPostgreSQLDialect}, this avoids
 *       the {@code OID} type mismatch.
 *   <li>{@link JpaSagaStore}: persists saga state as JPA entities, using the same
 *       Hibernate-corrected BYTEA binding path.
 *   <li>Transaction management is handled automatically by Spring's
 *       {@link PlatformTransactionManager} — Axon's JPA stores use the
 *       {@link EntityManager} obtained via the provider, which participates in
 *       the current Spring-managed transaction.
 * </ul>
 *
 * @see com.flashsale.commonlib.config.ByteaPostgreSQLDialect
 */
@Configuration
@RequiredArgsConstructor
public class AxonConfig {

    private final EntityManager entityManager;
    private final @Qualifier("xStreamSerializer") Serializer xStreamSerializer;

    /**
     * Token store that persists {@link org.axonframework.eventhandling.TrackingToken}
     * instances to the {@code token_entry} table managed by Flyway.
     *
     * <p>Uses {@link JpaTokenStore} with the injected XStream serializer so that
     * byte[] values are sent as native BYTEA parameters. The
     * {@link com.flashsale.commonlib.config.ByteaPostgreSQLDialect} ensures Hibernate
     * registers the correct VARBINARY/BYTEA descriptor for {@code SqlTypes.BLOB},
     * preventing the PostgreSQL OID type mismatch.
     */
    @Bean
    public TokenStore tokenStore() {
        return JpaTokenStore.builder()
                .entityManagerProvider(new SpringEntityManagerProvider())
                .serializer(xStreamSerializer)
                .build();
    }

    /**
     * Saga store that persists saga instances to the {@code saga_entry} table
     * managed by Flyway.
     */
    @Bean
    public SagaStore<?> sagaStore() {
        return JpaSagaStore.builder()
                .entityManagerProvider(new SpringEntityManagerProvider())
                .serializer(xStreamSerializer)
                .build();
    }

    /**
     * DeadlineManager bean required by OrderProcessingSaga to schedule
     * payment and shipping timeouts. Uses SimpleDeadlineManager (no Axon
     * Server dependency) with prototype scope so each saga instance gets
     * its own manager tied to its scope.
     */
    @Bean
    @Scope("prototype")
    public DeadlineManager deadlineManager(org.axonframework.config.Configuration axonConfiguration) {
        return SimpleDeadlineManager.builder()
                .scopeAwareProvider(axonConfiguration.scopeAwareProvider())
                .build();
    }

    /**
     * Bridges Axon's store interface to Spring's {@link EntityManager}.
     * Axon's JPA stores need this interface to obtain the active EntityManager
     * within their internal transaction lifecycle. The EntityManager is managed
     * by Spring's {@link PlatformTransactionManager}, so all Axon operations
     * participate in Spring-managed transactions automatically.
     */
    private class SpringEntityManagerProvider implements org.axonframework.common.jpa.EntityManagerProvider {
        @Override
        public jakarta.persistence.EntityManager getEntityManager() {
            return entityManager;
        }
    }
}
