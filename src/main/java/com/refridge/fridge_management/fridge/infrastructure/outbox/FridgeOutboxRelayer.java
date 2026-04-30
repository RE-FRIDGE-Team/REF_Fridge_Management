package com.refridge.fridge_management.fridge.infrastructure.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Fridge BC Outbox Relayer.
 *
 * <h2>역할</h2>
 * {@code fridge_pending_event} 테이블에서 PENDING 이벤트를 폴링해
 * Redis Stream에 {@code XADD}로 발행한다.
 *
 * <h2>동시성 제어</h2>
 * {@code SELECT FOR UPDATE SKIP LOCKED}로 다중 인스턴스 환경에서
 * 동일 row 중복 처리를 방지한다.
 *
 * <h2>재시도 정책</h2>
 * 발행 실패 시 {@code status=FAILED}, {@code retry_count} 증가.
 * 이후 폴링에서 PENDING 상태가 아니므로 자동 제외된다.
 * 별도 재시도 배치({@code retryFailed})가 최대 {@code MAX_RETRY_COUNT}회까지 재발행을 시도한다.
 *
 * <h2>메트릭</h2>
 * <ul>
 *   <li>{@code fridge.outbox.publish.success} — 발행 성공 카운터</li>
 *   <li>{@code fridge.outbox.publish.failure} — 발행 실패 카운터</li>
 * </ul>
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Slf4j
@Component
public class FridgeOutboxRelayer {

    private static final int BATCH_SIZE      = 100;
    private static final int MAX_RETRY_COUNT = 5;

    private final FridgePendingEventRepository repository;
    private final FridgeStreamPublisher publisher;
    private final Counter successCounter;
    private final Counter failureCounter;

    public FridgeOutboxRelayer(
            FridgePendingEventRepository repository,
            FridgeStreamPublisher publisher,
            MeterRegistry meterRegistry
    ) {
        this.repository     = repository;
        this.publisher      = publisher;
        this.successCounter = meterRegistry.counter("fridge.outbox.publish.success");
        this.failureCounter = meterRegistry.counter("fridge.outbox.publish.failure");
    }

    /**
     * PENDING 이벤트 발행 (2초 간격).
     *
     * <p>트랜잭션 안에서 SKIP LOCKED로 조회 → 발행 → 상태 업데이트까지 원자적으로 처리.
     */
    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void relay() {
        List<FridgePendingEvent> batch =
                repository.findPendingForUpdateSkipLocked(BATCH_SIZE);

        if (batch.isEmpty()) return;

        log.debug("[FridgeOutboxRelayer] 발행 대상 {}건 처리 시작", batch.size());

        for (FridgePendingEvent event : batch) {
            try {
                publisher.publish(event.getStreamKey(), event.getPayload());
                event.markPublished();
                successCounter.increment();
            } catch (Exception ex) {
                log.error("[FridgeOutboxRelayer] 발행 실패. eventId={}, eventType={}, error={}",
                        event.getEventId(), event.getEventType(), ex.getMessage());
                event.markFailed(ex.getMessage());
                failureCounter.increment();
            }
        }

        repository.saveAll(batch);
    }

    /**
     * FAILED 이벤트 재시도 (1분 간격).
     *
     * <p>최대 {@code MAX_RETRY_COUNT}회 이하의 실패 이벤트만 재발행 시도한다.
     * 횟수 초과 이벤트는 운영팀이 수동 확인 후 처리.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void retryFailed() {
        List<FridgePendingEvent> failedBatch =
                repository.findFailedForRetry(MAX_RETRY_COUNT);

        if (failedBatch.isEmpty()) return;

        log.warn("[FridgeOutboxRelayer] FAILED 이벤트 재시도. {}건", failedBatch.size());

        for (FridgePendingEvent event : failedBatch) {
            event.resetToPending();   // FAILED → PENDING 리셋, 다음 relay()에서 재처리
        }

        repository.saveAll(failedBatch);
    }
}