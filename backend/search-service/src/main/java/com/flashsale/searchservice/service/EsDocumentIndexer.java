package com.flashsale.searchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptSource;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import com.flashsale.searchservice.domain.model.SearchDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EsDocumentIndexer {

    private final ElasticsearchClient esClient;

    @Value("${search.elasticsearch.index-name:skus}")
    private String indexName;

    public void indexDocument(SearchDocument doc) throws IOException {
        esClient.index(i -> i
                .index(indexName)
                .id(doc.getSkuId())
                .document(doc)
        );
    }

    public void bulkIndex(List<SearchDocument> documents) throws IOException {
        bulkIndexInto(documents, indexName);
    }

    public void bulkIndexInto(List<SearchDocument> documents, String targetIndex) throws IOException {
        if (documents == null || documents.isEmpty()) return;

        List<BulkOperation> ops = documents.stream()
                .map(doc -> BulkOperation.of(op -> op
                        .index(IndexOperation.of(idx -> idx
                                .index(targetIndex)
                                .id(doc.getSkuId())
                                .document(doc)
                        ))
                ))
                .toList();

        BulkRequest bulkReq = BulkRequest.of(b -> b.operations(ops));
        BulkResponse bulkResp = esClient.bulk(bulkReq);

        if (bulkResp.errors()) {
            bulkResp.items().stream()
                    .filter(item -> item.error() != null)
                    .forEach(item -> log.error("Bulk index error for id {}: {}",
                            item.id(), item.error().reason()));
        }
    }

    public void deleteByProductId(String productId) throws IOException {
        esClient.deleteByQuery(DeleteByQueryRequest.of(d -> d
                .index(indexName)
                .query(TermQuery.of(t -> t.field("productId").value(productId))._toQuery())
        ));
    }

    public void setActiveByProductId(String productId, boolean active) throws IOException {
        Script script = buildSimpleScript("ctx._source.isActive = " + active);
        esClient.updateByQuery(u -> u
                .index(indexName)
                .query(TermQuery.of(t -> t.field("productId").value(productId))._toQuery())
                .script(script)
        );
    }

    public void updateByProductId(String productId, Map<String, Object> fields) throws IOException {
        Script script = buildUpdateScript(fields);
        esClient.updateByQuery(u -> u
                .index(indexName)
                .query(TermQuery.of(t -> t.field("productId").value(productId))._toQuery())
                .script(script)
        );
    }

    public void updateByCategoryId(String categoryId, Map<String, Object> fields) throws IOException {
        Script script = buildUpdateScript(fields);
        esClient.updateByQuery(u -> u
                .index(indexName)
                .query(TermQuery.of(t -> t.field("categoryId").value(categoryId))._toQuery())
                .script(script)
        );
    }

    public void partialUpdate(String skuId, Map<String, Object> fields) throws IOException {
        UpdateRequest<SearchDocument, Map<String, Object>> req = UpdateRequest.of(r -> r
                .index(indexName)
                .id(skuId)
                .doc(fields)
        );
        esClient.update(req, SearchDocument.class);
    }

    public void bulkPartialUpdateFlashSaleActivate(List<Map<String, Object>> items) throws IOException {
        List<BulkOperation> ops = items.stream()
                .map(item -> {
                    String scriptSource = "ctx._source.price = params.flashPrice; ctx._source.originalPrice = params.originalPrice; ctx._source.hasDiscount = params.hasDiscount; ctx._source.flashSessionId = params.sessionId";
                    Script script = Script.of(s -> s
                            .lang("painless")
                            .source(ScriptSource.of(ss -> ss.scriptString(scriptSource)))
                            .params(toJsonParams(item))
                    );
                    return BulkOperation.of(op -> op
                            .update(UpdateOperation.of(u -> u
                                    .index(indexName)
                                    .id((String) item.get("skuId"))
                                    .action(a -> a.script(script))
                             ))
                    );
                })
                .toList();

        if (!ops.isEmpty()) {
            BulkResponse resp = esClient.bulk(BulkRequest.of(b -> b.operations(ops)));
            if (resp.errors()) {
                log.error("Bulk flash sale update errors: {}", resp.items().stream()
                        .filter(i -> i.error() != null)
                        .map(i -> i.error().reason())
                        .collect(Collectors.joining("; ")));
            }
        }
    }

    public void bulkPartialUpdateFlashSaleDeactivate(List<String> skuIds, Integer sessionId) throws IOException {
        List<BulkOperation> ops = skuIds.stream()
                .map(skuId -> {
                    Script script = Script.of(s -> s
                            .lang("painless")
                            .source(ScriptSource.of(ss -> ss.scriptString(
                                    "ctx._source.price = ctx._source.originalPrice; ctx._source.hasDiscount = false; ctx._source.flashSessionId = null"
                            )))
                    );
                    return BulkOperation.of(op -> op
                            .update(UpdateOperation.of(u -> u
                                    .index(indexName)
                                    .id(skuId)
                                    .action(a -> a.script(script))
                            ))
                    );
                })
                .toList();

        if (!ops.isEmpty()) {
            BulkResponse resp = esClient.bulk(BulkRequest.of(b -> b.operations(ops)));
            if (resp.errors()) {
                log.error("Bulk flash sale deactivate errors: {}", resp.items().stream()
                        .filter(i -> i.error() != null)
                        .map(i -> i.error().reason())
                        .collect(Collectors.joining("; ")));
            }
        }
    }

    private Script buildSimpleScript(String sourceCode) {
        return Script.of(s -> s
                .lang("painless")
                .source(ScriptSource.of(ss -> ss.scriptString(sourceCode)))
        );
    }

    private Script buildUpdateScript(Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder();
        for (String key : fields.keySet()) {
            sb.append("ctx._source['").append(key).append("'] = params.'").append(key).append("'; ");
        }
        return Script.of(s -> s
                .lang("painless")
                .source(ScriptSource.of(ss -> ss.scriptString(sb.toString())))
                .params(toJsonParams(fields))
        );
    }

    private Map<String, JsonData> toJsonParams(Map<String, Object> map) {
        Map<String, JsonData> result = new HashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getValue() != null) {
                result.put(e.getKey(), JsonData.of(e.getValue()));
            } else {
                result.put(e.getKey(), JsonData.of((String) null));
            }
        }
        return result;
    }
}
