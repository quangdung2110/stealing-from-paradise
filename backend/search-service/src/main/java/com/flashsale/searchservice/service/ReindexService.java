package com.flashsale.searchservice.service;

import com.flashsale.searchservice.domain.model.SearchDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReindexService {

    private final ElasticsearchService esService;
    private final ProductServiceClient productServiceClient;

    @Value("${search.elasticsearch.index-name:skus}")
    private String indexName;

    private final Map<String, ReindexStatus> reindexStatuses = new ConcurrentHashMap<>();

    public ReindexStatus getStatus() {
        ReindexStatus status = reindexStatuses.get("default");
        if (status == null) {
            return ReindexStatus.builder()
                    .status("NOT_STARTED")
                    .message("No reindex has been performed yet")
                    .build();
        }
        return status;
    }

    public ReindexStatus triggerReindex() {
        ReindexStatus current = getStatus();
        if ("IN_PROGRESS".equals(current.getStatus())) {
            return ReindexStatus.builder()
                    .status("IN_PROGRESS")
                    .message("Reindex already running")
                    .build();
        }

        ReindexStatus status = ReindexStatus.builder()
                .status("IN_PROGRESS")
                .startedAt(Instant.now())
                .build();
        reindexStatuses.put("default", status);

        executeReindexAsync(status);

        ReindexStatus latestStatus = getStatus();
        if (!"IN_PROGRESS".equals(latestStatus.getStatus())) {
            return latestStatus;
        }

        return ReindexStatus.builder()
                .status("IN_PROGRESS")
                .message("Reindex started")
                .build();
    }

    @Async
    public void executeReindexAsync(ReindexStatus status) {
        try {
            log.info("Starting full reindex...");
            long start = System.currentTimeMillis();

            List<SearchDocument> documents = productServiceClient.fetchAllActiveProducts();
            if (documents.isEmpty()) {
                log.warn("No documents to index from Product Service");
                status.setStatus("COMPLETED");
                status.setDocumentCount(0);
                status.setDurationMs(System.currentTimeMillis() - start);
                status.setMessage("No active products found");
                return;
            }

            String newIndex = indexName + "_v" + Instant.now().toEpochMilli();
            String oldIndex = esService.getCurrentIndexForAliasOrConcreteIndex(indexName);

            esService.createIndexAs(newIndex);

            int batchSize = 500;
            int totalIndexed = 0;
            for (int i = 0; i < documents.size(); i += batchSize) {
                int end = Math.min(i + batchSize, documents.size());
                List<SearchDocument> batch = documents.subList(i, end);
                esService.bulkIndexInto(batch, newIndex);
                totalIndexed += batch.size();
                log.info("Indexed batch {}-{} / {} total", i, end, documents.size());
            }

            esService.swapAlias(indexName, oldIndex, newIndex);

            long duration = System.currentTimeMillis() - start;
            status.setStatus("COMPLETED");
            status.setDocumentCount(totalIndexed);
            status.setDurationMs(duration);
            status.setMessage("Reindex completed successfully");

            log.info("Reindex completed: {} documents in {}ms", totalIndexed, duration);

        } catch (Exception e) {
            log.error("Reindex failed: {}", e.getMessage(), e);
            status.setStatus("FAILED");
            status.setMessage("Reindex failed: " + e.getMessage());
            status.setDurationMs(
                    status.getStartedAt() != null
                            ? System.currentTimeMillis() - status.getStartedAt().toEpochMilli()
                            : 0L
            );
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReindexStatus {
        private String status;
        private Integer documentCount;
        private Long durationMs;
        private String message;
        private Instant startedAt;
    }
}
