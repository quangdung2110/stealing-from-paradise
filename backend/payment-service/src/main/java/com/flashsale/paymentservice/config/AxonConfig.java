package com.flashsale.paymentservice.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.jpa.JpaSagaStore;
import org.axonframework.serialization.Serializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Axon Framework configuration for the Payment domain service.
 */
@Configuration
@RequiredArgsConstructor
public class AxonConfig {

    private final EntityManager entityManager;
    private final @Qualifier("xStreamSerializer") Serializer xStreamSerializer;

    @Bean
    public TokenStore tokenStore() {
        return JpaTokenStore.builder()
                .entityManagerProvider(new SpringEntityManagerProvider())
                .serializer(xStreamSerializer)
                .build();
    }

    @Bean
    public SagaStore<?> sagaStore() {
        return JpaSagaStore.builder()
                .entityManagerProvider(new SpringEntityManagerProvider())
                .serializer(xStreamSerializer)
                .build();
    }

    private class SpringEntityManagerProvider implements org.axonframework.common.jpa.EntityManagerProvider {
        @Override
        public jakarta.persistence.EntityManager getEntityManager() {
            return entityManager;
        }
    }
}
