package com.flashsale.searchservice.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.searchservice.service.ProductServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SearchIndexDataResponseConsumer {

    private final ProductServiceClient productServiceClient;

    @KafkaListener(
            topics = KafkaTopics.SEARCH_INDEX_DATA_RESPONSE,
            containerFactory = "searchIndexDataReplyKafkaListenerContainerFactory"
    )
    public void onMessage(String message) {
        productServiceClient.consumeSearchIndexDataResponse(message);
    }
}
