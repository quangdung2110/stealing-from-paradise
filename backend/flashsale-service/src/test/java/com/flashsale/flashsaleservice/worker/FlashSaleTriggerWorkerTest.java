package com.flashsale.flashsaleservice.worker;

import com.flashsale.flashsaleservice.service.FlashSaleSessionStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlashSaleTriggerWorkerTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private FlashSaleSessionStateService stateService;

    @InjectMocks
    private FlashSaleTriggerWorker worker;

    @BeforeEach
    void setUp() {
        // stateService returns Mono; stub with empty so production code's .block()
        // doesn't NPE on the default Mockito null return for Mono methods.
        lenient().when(stateService.activate(anyLong())).thenReturn(Mono.empty());
        lenient().when(stateService.end(anyLong())).thenReturn(Mono.empty());
        lenient().when(stateService.closeRegistration(anyLong())).thenReturn(Mono.empty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickShouldDispatchStartTrigger() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.<Object>of("start:42")));

        worker.tick();

        verify(stateService).activate(42L);
        verify(stateService, never()).end(any());
        verify(stateService, never()).closeRegistration(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickShouldDispatchEndTrigger() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.<Object>of("end:7")));

        worker.tick();

        verify(stateService).end(7L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickShouldDispatchCloseRegTrigger() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.<Object>of("close_reg:99")));

        worker.tick();

        verify(stateService).closeRegistration(99L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickShouldHandleMultipleTriggers() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.<Object>of("start:1", "end:2", "close_reg:3")));

        worker.tick();

        verify(stateService).activate(1L);
        verify(stateService).end(2L);
        verify(stateService).closeRegistration(3L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickShouldIgnoreInvalidFormat() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.<Object>of("garbage", "no-colon", ":noid", "type:")));

        worker.tick();

        verifyNoInteractions(stateService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickShouldHandleEmptyResult() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.empty());

        worker.tick();

        verifyNoInteractions(stateService);
    }
}
