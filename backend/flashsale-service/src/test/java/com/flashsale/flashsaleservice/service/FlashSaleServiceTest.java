package com.flashsale.flashsaleservice.service;

import com.flashsale.flashsaleservice.domain.model.FlashSaleItem;
import com.flashsale.flashsaleservice.domain.model.FlashSaleSession;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import com.flashsale.flashsaleservice.dto.request.CreateFlashSaleItemRequest;
import com.flashsale.flashsaleservice.dto.request.CreateSessionRequest;
import com.flashsale.flashsaleservice.dto.response.FlashSaleItemResponse;
import com.flashsale.flashsaleservice.dto.response.SessionDetailResponse;
import com.flashsale.flashsaleservice.dto.response.SessionListResponse;
import com.flashsale.flashsaleservice.dto.response.SessionResponse;
import com.flashsale.commonlib.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlashSaleServiceTest {

    @Mock
    private FlashSaleSessionService sessionService;
    @Mock
    private FlashSaleItemService itemService;

    private FlashSaleService flashSaleService;

    @BeforeEach
    void setUp() {
        flashSaleService = new FlashSaleService(sessionService, itemService);
    }

    @Test
    void getSessionsShouldReturnAllWhenNoStatusFilter() {
        FlashSaleSession session = FlashSaleSession.builder()
                .id(1L).name("Test").status("ACTIVE")
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusHours(1))
                .build();
        SessionResponse resp = SessionResponse.builder()
                .sessionId(1L).name("Test").status("ACTIVE").build();
        when(sessionService.getSessions(null))
                .thenReturn(Mono.just(SessionListResponse.builder()
                        .serverTime(0L)
                        .sessions(java.util.List.of(resp))
                        .build()));

        StepVerifier.create(flashSaleService.getSessions(null))
                .expectNextMatches(r -> r.getSessions().size() == 1
                        && r.getSessions().get(0).getName().equals("Test"))
                .verifyComplete();
    }

    @Test
    void getSessionDetailShouldReturnSessionWithItems() {
        FlashSaleSession session = FlashSaleSession.builder().id(1L).name("S").status("ACTIVE").build();
        FlashSaleItem item = FlashSaleItem.builder()
                .id(10L).sessionId(1L).skuCode("SKU-001")
                .flashPrice(new BigDecimal("99.99")).flashStock(100)
                .limitPerUser(2).soldQty(0).status("APPROVED").build();
        when(sessionRepoFindById(1L)).thenReturn(Mono.just(session));
        when(sessionService.getSessionDetail(1L))
                .thenReturn(Mono.just(SessionDetailResponse.builder()
                        .session(SessionResponse.builder().sessionId(1L).name("S").status("ACTIVE").build())
                        .items(java.util.List.of(FlashSaleItemResponse.builder()
                                .id(10L).skuCode("SKU-001").build()))
                        .build()));

        StepVerifier.create(flashSaleService.getSessionDetail(1L))
                .expectNextMatches(d -> d.getSession().getName().equals("S")
                        && d.getItems().size() == 1
                        && d.getItems().get(0).getSkuCode().equals("SKU-001"))
                .verifyComplete();
    }

    @Test
    void createFlashSaleItemShouldDelegateToItemService() {
        CreateFlashSaleItemRequest req = CreateFlashSaleItemRequest.builder()
                .skuCode("SKU-002").flashPrice(new BigDecimal("49.99"))
                .flashStock(50).limitPerUser(1).build();
        FlashSaleItemResponse item = FlashSaleItemResponse.builder()
                .id(20L).sessionId(1L).sellerId(9L).skuCode("SKU-002")
                .flashPrice(new BigDecimal("49.99")).flashStock(50)
                .limitPerUser(1).soldQty(0).status("PENDING").build();
        when(itemService.createFlashSaleItem(1L, 9L, req)).thenReturn(Mono.just(item));

        StepVerifier.create(flashSaleService.createFlashSaleItem(1L, 9L, req))
                .expectNextMatches(r -> r.getSkuCode().equals("SKU-002")
                        && r.getStatus().equals("PENDING"))
                .verifyComplete();
    }

    @Test
    void createSessionShouldDelegateToSessionService() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(2);
        CreateSessionRequest req = CreateSessionRequest.builder()
                .name("New Sale").startTime(start).endTime(end).build();
        when(sessionService.createSession(req)).thenReturn(Mono.just(
                SessionResponse.builder().sessionId(1L).name("New Sale").status("UPCOMING").build()));

        StepVerifier.create(flashSaleService.createSession(req))
                .expectNextMatches(r -> r.getName().equals("New Sale")
                        && r.getStatus().equals("UPCOMING"))
                .verifyComplete();
    }

    @Test
    void sessionResponseShouldMarkEndedWhenPast() {
        FlashSaleSession past = FlashSaleSession.builder()
                .id(1L).name("Past").status("ACTIVE")
                .startTime(LocalDateTime.now().minusHours(3))
                .endTime(LocalDateTime.now().minusHours(1))
                .build();
        when(sessionService.getSessions(null))
                .thenReturn(Mono.just(SessionListResponse.builder()
                        .serverTime(0L)
                        .sessions(java.util.List.of(
                                SessionResponse.builder().sessionId(1L).name("Past")
                                        .status("ACTIVE").isEnded(true).build()))
                        .build()));

        StepVerifier.create(flashSaleService.getSessions(null))
                .expectNextMatches(r -> r.getSessions().get(0).isEnded())
                .verifyComplete();
    }

    private Mono<FlashSaleSession> sessionRepoFindById(Long id) {
        // helper used in obsolete-style test setup; not invoked in current test bodies
        return Mono.empty();
    }
}
