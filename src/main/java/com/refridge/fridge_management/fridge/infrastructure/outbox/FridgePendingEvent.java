package com.refridge.fridge_management.fridge.infrastructure.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fridge BC Outbox 이벤트 엔티티.
 *
 * <h2>역할</h2>
 * 도메인 이벤트를 Redis Stream으로 발행하기 전
 * DB 테이블({@code fridge_pending_event})에 먼저 저장해
 * 비즈니스 트랜잭션과 이벤트 발행의 원자성을 보장한다.
 *
 * <h2>상태 전이</h2>
 * <pre>
 * PENDING → PUBLISHED  (정상 발행)
 * PENDING → FAILED     (발행 실패, retry_count 증가)
 * FAILED  → PENDING    (재시도 - 향후 Dead Letter Queue 구현 시)
 * </pre>
 *
 * <h2>SKIP LOCKED</h2>
 * {@code FridgeOutboxRelayer}가 {@code SELECT FOR UPDATE SKIP LOCKED}로
 * 다중 인스턴스 환경에서 동일 row 중복 처리를 방지한다.
 *
 * @author 승훈
 * @since 2026-04-26
 * @see FridgeOutboxAppender
 * @see FridgeOutboxRelayer
 */
@Entity
@Table(
        name = "fridge_pending_event",
        schema = "fridge_schema",
        indexes = @Index(name = "idx_pending", columnList = "status, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FridgePendingEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    /** 항상 'FRIDGE' */
    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 40)
    private String aggregateType;

    /** fridgeId */
    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 36)
    private String aggregateId;

    /** 이벤트 클래스 단순명 (ex: 'FridgeItemConsumedEvent') */
    @Column(name = "event_type", nullable = false, updatable = false, length = 80)
    private String eventType;

    /** Redis Stream key (ex: 'fridge:consumed') */
    @Column(name = "stream_key", nullable = false, updatable = false, length = 80)
    private String streamKey;

    /**
     * 이벤트 record를 JSON 직렬화한 페이로드.
     * PostgreSQL JSONB 타입 사용 — JSON보다 저장 효율·조회 성능 우수.
     * @Lob 제거: @Lob은 Hibernate가 CLOB으로 매핑해 JSONB와 타입 불일치 발생.
     * columnDefinition = "jsonb"로 직접 지정해 Hibernate 검증을 통과시킨다.
     */
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    // ── 팩토리 ────────────────────────────────────────────────────────

    public static FridgePendingEvent of(
            String eventId,
            String aggregateId,
            String eventType,
            String streamKey,
            String payload
    ) {
        FridgePendingEvent e = new FridgePendingEvent();
        e.eventId       = Objects.requireNonNull(eventId, "eventId");
        e.aggregateType = "FRIDGE";
        e.aggregateId   = Objects.requireNonNull(aggregateId, "aggregateId");
        e.eventType     = Objects.requireNonNull(eventType, "eventType");
        e.streamKey     = Objects.requireNonNull(streamKey, "streamKey");
        e.payload       = Objects.requireNonNull(payload, "payload");
        e.status        = OutboxStatus.PENDING;
        e.retryCount    = 0;
        e.createdAt     = Instant.now();
        return e;
    }

    // ── 상태 전이 ─────────────────────────────────────────────────────

    /** Redis Stream 발행 성공 시 호출 */
    public void markPublished() {
        this.status      = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError   = null;
    }

    /**
     * 발행 실패 시 호출.
     * retry_count를 증가시키고 에러 메시지를 기록한다.
     * 최대 500자로 잘라서 저장.
     */
    public void markFailed(String errorMessage) {
        this.status     = OutboxStatus.FAILED;
        this.retryCount = this.retryCount + 1;
        this.lastError  = errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500)
                : errorMessage;
    }

    /** 재시도를 위해 FAILED → PENDING으로 리셋 */
    public void resetToPending() {
        this.status    = OutboxStatus.PENDING;
        this.lastError = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FridgePendingEvent p)) return false;
        return Objects.equals(eventId, p.eventId);
    }

    @Override
    public int hashCode() { return Objects.hash(eventId); }

    /**
     * Outbox 이벤트 처리 상태.
     */
    public enum OutboxStatus {
        /** 발행 대기 중 */
        PENDING,
        /** Redis Stream 발행 완료 */
        PUBLISHED,
        /** 발행 실패 (retry_count 참고) */
        FAILED
    }
}