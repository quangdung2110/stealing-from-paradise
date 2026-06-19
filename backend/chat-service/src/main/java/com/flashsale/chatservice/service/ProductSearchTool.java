package com.flashsale.chatservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Level 1 Tool: Product search -- calls search-service API.
 * No authentication required. Returns product list as JSON.
 *
 * NOTE: This file physically lives in the service package because the tools/
 * directory does not yet exist. Move to com.flashsale.chatservice.tools
 * after creating src/main/java/com/flashsale/chatservice/tools/
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductSearchTool {

    private final WebClient.Builder webClientBuilder;

    @Tool(description = "Search for products by keyword or name. Use when the user asks about products, wants to browse the catalog, or search for specific items.")
    public String searchProducts(@ToolParam(description = "Search query string, e.g. 'iPhone' or 'giay the thao'") String query) {
        log.info("[ProductSearchTool] Searching products with query: {}", query);
        try {
            String result = webClientBuilder.build()
                    .get()
                    .uri("http://search-service/search/products?q={query}", query)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorReturn("{\"error\": \"Search service unavailable\", \"products\": []}")
                    .block();
            log.info("[ProductSearchTool] Search completed for query: {}", query);
            return result;
        } catch (Exception e) {
            log.error("[ProductSearchTool] Search failed for query: {}", query, e);
            return "{\"error\": \"" + e.getMessage() + "\", \"products\": []}";
        }
    }

    @Tool(description = "Get autocomplete suggestions for product search. Use when the user is typing a search query.")
    public String suggestProducts(@ToolParam(description = "Partial search query for autocomplete") String query) {
        log.info("[ProductSearchTool] Getting suggestions for: {}", query);
        try {
            return webClientBuilder.build()
                    .get()
                    .uri("http://search-service/search/products/suggest?q={query}", query)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorReturn("{\"suggestions\": []}")
                    .block();
        } catch (Exception e) {
            log.error("[ProductSearchTool] Suggest failed for query: {}", query, e);
            return "{\"suggestions\": []}";
        }
    }
}
