package com.refridge.fridge_management.fridge.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;

/**
 * Fridge Outbox 이벤트 리포지토리.
 *
 * <h2>SKIP LOCKED 전략</h2>
 * {@code findPendingForUpdateSkipLocked}는 다중 인스턴스 환경에서
 * 동일 row를 중복으로 처리하지 않도록 {@code SELECT FOR UPDATE SKIP LOCKED}를 사용한다.
 * Hibernate 6.x (Spring Boot 4.x)에서는 {@code @QueryHint}로 SKIP_LOCKED를 지정한다.
 *
 * <h2>재시도 대상 조회</h2>
 * {@code findFailedForRetry}는 FAILED 상태의 이벤트를 조회해
 * Dead Letter Queue 처리 또는 재발행에 사용할 수 있다 (향후 구현).
 *
 * @author 승훈
 * @since 2026-04-29
 */
public interface FridgePendingEventRepository
        extends JpaRepository<FridgePendingEvent, String> {

    /**
     * PENDING 이벤트 배치 조회 (FOR UPDATE SKIP LOCKED).
     *
     * <h3>SKIP LOCKED 동작</h3>
     * 다른 인스턴스가 이미 처리 중인 row는 건너뛴다.
     * 따라서 다중 인스턴스 배포 시 동일 이벤트 중복 발행이 방지된다.
     *
     * @param batchSize 한 번에 처리할 최대 이벤트 수
     * @return PENDING 상태 이벤트 목록
     */
    @Query(value = """
            SELECT e FROM FridgePendingEvent e
            WHERE e.status = 'PENDING'
            ORDER BY e.createdAt ASC
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<FridgePendingEvent> findPendingForUpdateSkipLocked(
            @Param("batchSize") int batchSize
    );

    /**
     * FAILED 이벤트 조회 (재시도 대상).
     *
     * @param maxRetryCount 이 횟수 이하인 이벤트만 조회 (무한 재시도 방지)
     */
    @Query("""
            SELECT e FROM FridgePendingEvent e
            WHERE e.status = 'FAILED'
              AND e.retryCount <= :maxRetryCount
            ORDER BY e.createdAt ASC
            """)
    List<FridgePendingEvent> findFailedForRetry(@Param("maxRetryCount") int maxRetryCount);

    /**
     * 발행 완료된 이벤트 수 조회 (모니터링용).
     */
    long countByStatus(FridgePendingEvent.OutboxStatus status);
}