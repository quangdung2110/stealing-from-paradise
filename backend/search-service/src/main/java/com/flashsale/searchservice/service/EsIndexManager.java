package com.flashsale.searchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class EsIndexManager {

    private final ElasticsearchClient esClient;

    @Value("${search.elasticsearch.index-name:skus}")
    private String indexName;

    @Value("${search.elasticsearch.max-result-window:10000}")
    private int maxResultWindow;

    public void createIndexIfNotExists() {
        try {
            ensureAlias(indexName);
        } catch (IOException e) {
            log.warn("Cannot check/create ES index: {}", e.getMessage());
        }
    }

    public void createIndex() throws IOException {
        createIndexAs(indexName);
    }

    public void createIndexAs(String targetIndex) throws IOException {
        esClient.indices().create(CreateIndexRequest.of(c -> c
                .index(targetIndex)
                .settings(s -> s
                        .numberOfShards("3")
                        .numberOfReplicas("1")
                        .maxResultWindow(maxResultWindow)
                        .analysis(a -> a
                                .analyzer("vietnamese_analyzer", an -> an
                                        .custom(cu -> cu
                                                // ICU tokenizer handles Unicode text segmentation (UAX #29),
                                                // correctly splitting Vietnamese text with diacritics.
                                                .tokenizer("icu_tokenizer")
                                                // ICU folding normalises Unicode characters to their ASCII
                                                // equivalents (e.g. "tài" → "tai") so queries without diacritics
                                                // still match indexed content with diacritics, and vice versa.
                                                // Built-in asciifolding is a simpler subset; ICU folding is
                                                // comprehensive for all scripts (Latin, CJK, etc.).
                                                .filter("lowercase", "icu_folding")
                                        )
                                )
                        )
                        // Explicit BM25 similarity configuration for relevance ranking.
                        // The default parameters (k1=1.2, b=0.75) are retained here to
                        // document the algorithm selection and make tuning easier in future.
                        .similarity("custom_bm25", sim -> sim
                                .bm25(b -> b
                                        .k1(1.2)   // Saturation point — how much term frequency matters
                                        .b(0.75)   // Length normalisation (0=no norm, 1=full norm)
                                )
                        )
                )
                .mappings(m -> m
                        .properties("skuId", p -> p.keyword(k -> k))
                        .properties("productId", p -> p.keyword(k -> k))
                        .properties("sellerId", p -> p.long_(l -> l))
                        .properties("productName", p -> p
                                .text(t -> t
                                        .analyzer("vietnamese_analyzer")
                                        .similarity("custom_bm25")
                                        .fields("keyword", f -> f.keyword(k -> k))
                                )
                        )
                        .properties("productSlug", p -> p.keyword(k -> k))
                        .properties("productDescription", p -> p
                                .text(t -> t
                                        .analyzer("vietnamese_analyzer")
                                        .similarity("custom_bm25")
                                )
                        )
                        .properties("productAttributes", p -> p.object(o -> o.enabled(true)))
                        .properties("categoryId", p -> p.keyword(k -> k))
                        .properties("categorySlug", p -> p.keyword(k -> k))
                        .properties("categoryPath", p -> p.keyword(k -> k))
                        .properties("categorySlugPath", p -> p.keyword(k -> k))
                        .properties("categoryName", p -> p.keyword(k -> k))
                        .properties("variantAttributes", p -> p.object(o -> o.enabled(true)))
                        .properties("skuCode", p -> p.keyword(k -> k))
                        .properties("price", p -> p.double_(d -> d))
                        .properties("originalPrice", p -> p.double_(d -> d))
                        .properties("hasDiscount", p -> p.boolean_(b -> b))
                        .properties("flashSessionId", p -> p.keyword(k -> k))
                        .properties("stockStatus", p -> p.keyword(k -> k))
                        .properties("productStatus", p -> p.keyword(k -> k))
                        .properties("skuStatus", p -> p.keyword(k -> k))
                        .properties("isActive", p -> p.boolean_(b -> b))
                        .properties("thumbnailUrl", p -> p.keyword(k -> k.index(false)))
                        .properties("skuImageUrl", p -> p.keyword(k -> k.index(false)))
                        .properties("sellerName", p -> p
                                .text(t -> t
                                        .fields("keyword", f -> f.keyword(k -> k))
                                )
                        )
                        .properties("sortId", p -> p.integer(i -> i))
                )
        ));
        log.info("ES index '{}' created successfully", targetIndex);
    }

    public void ensureAlias(String aliasName) throws IOException {
        if (aliasExists(aliasName)) {
            return;
        }

        if (indexExists(aliasName)) {
            log.info("ES concrete index '{}' exists without alias; next reindex will migrate it to an alias-backed index",
                    aliasName);
            return;
        }

        String initialIndex = aliasName + "_v" + Instant.now().toEpochMilli();
        createIndexAs(initialIndex);
        esClient.indices().putAlias(r -> r.index(initialIndex).name(aliasName));
        log.info("Created alias '{}' -> '{}'", aliasName, initialIndex);
    }

    public String getCurrentIndexForAlias(String aliasName) throws IOException {
        if (!aliasExists(aliasName)) {
            return null;
        }
        var aliasResp = esClient.indices().getAlias(GetAliasRequest.of(g -> g.name(aliasName)));
        if (aliasResp.aliases().isEmpty()) {
            return null;
        }
        return aliasResp.aliases().keySet().iterator().next();
    }

    public String getCurrentIndexForAliasOrConcreteIndex(String aliasName) throws IOException {
        String currentAliasTarget = getCurrentIndexForAlias(aliasName);
        if (currentAliasTarget != null) {
            return currentAliasTarget;
        }
        return indexExists(aliasName) ? aliasName : null;
    }

    public void swapAlias(String aliasName, String oldIndex, String newIndex) throws IOException {
        boolean concreteIndexBlocksAlias = oldIndex != null
                && oldIndex.equals(aliasName)
                && indexExists(aliasName)
                && !aliasExists(aliasName);
        if (concreteIndexBlocksAlias) {
            esClient.indices().delete(d -> d.index(aliasName));
            oldIndex = null;
            log.info("Deleted legacy concrete index '{}' before creating alias", aliasName);
        }

        String previousIndex = oldIndex;
        esClient.indices().updateAliases(r -> {
            if (previousIndex != null && !previousIndex.isBlank()) {
                r.actions(a -> a.remove(rem -> rem.index(previousIndex).alias(aliasName)));
            }
            return r.actions(a -> a.add(add -> add.index(newIndex).alias(aliasName)));
        });
        log.info("Alias swap: '{}' -> '{}'", aliasName, newIndex);
    }

    public boolean indexExists(String index) throws IOException {
        return esClient.indices().exists(ExistsRequest.of(e -> e.index(index))).value();
    }

    public boolean aliasExists(String aliasName) throws IOException {
        return esClient.indices().existsAlias(e -> e.name(aliasName)).value();
    }
}
