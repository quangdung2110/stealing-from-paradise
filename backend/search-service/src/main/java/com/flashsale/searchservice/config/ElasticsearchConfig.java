package com.flashsale.searchservice.config;

import com.flashsale.searchservice.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class ElasticsearchConfig {

    @Bean
    public CommandLineRunner initializeElasticsearchIndex(ElasticsearchService esService) {
        return args -> {
            try {
                log.info("Initializing Elasticsearch index 'skus'...");
                esService.createIndexIfNotExists();
                esService.ensureAlias("skus");
                log.info("ES index 'skus' initialized successfully");
            } catch (Exception e) {
                log.warn("Cannot initialize ES index (service may not be running yet): {}", e.getMessage());
            }
        };
    }
}
