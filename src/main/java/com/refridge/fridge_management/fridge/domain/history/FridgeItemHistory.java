package com.refridge.fridge_management.fridge.domain.history;

import tools.jackson.databind.JsonNode;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 냉장고 아이템 이력 엔티티.
 *
 * <h2>역할</h2>
 * FridgeItem의 주요 상태 변화 이력을 기록한다.
 * 소비기한 연장 추천 시 LLM이 아이템의 맥락(개봉 여부 추정, 보관 환경 등)을
 * 판단할 수 있도록 {@code FridgeItemContext}에 포함되어 전달된다.
 *
 * <h2>기록 대상 이벤트</h2>
 * <ul>
 *   <li>{@code ADDED}         — 냉장고에 추가됨 (채우기 완료)</li>
 *   <li>{@code MOVED}         — 구역 이동 (from/to)</li>
 *   <li>{@code PORTIONED}     — 소분됨 (개봉 추정)</li>
 *   <li>{@code COOKED}        — 요리 재료로 일부 사용됨 (개봉 추정)</li>
 *   <li>{@code SHELF_EXTENDED}— 소비기한 연장됨</li>
 * </ul>
 * terminal 이벤트(CONSUMED, DISPOSED)는 조회 자체가 의미 없어 기록하지 않는다.
 *
 * <h2>OCP 설계</h2>
 * 도메인 레이어를 변경하지 않고 인프라 레이어의
 * {@code FridgeItemHistoryAppender}(@TransactionalEventListener)가
 * 기존 도메인 이벤트를 재활용해 이 엔티티를 INSERT한다.
 * 새로운 이력 유형이 추가되더라도 도메인 레이어 수정 없이
 * Appender의 switch case만 확장하면 된다.
 *
 * <h2>payload 설계</h2>
 * 이벤트 유형별 세부 정보를 JSON으로 저장한다.
 * <pre>
 * ADDED:          { "sectionType": "REFRIGERATED", "processingType": "RAW" }
 * MOVED:          { "from": "FREEZER", "to": "REFRIGERATED" }
 * PORTIONED:      { "portionCount": 3 }
 * COOKED:         { "cookedFridgeItemId": "uuid" }
 * SHELF_EXTENDED: { "additionalDays": 7, "newExpiresAt": "2026-06-01" }
 * </pre>
 *
 * @author 승훈
 * @since 2026-04-26
 * @see HistoryEventType
 * @see com.refridge.fridge_management.fridge.domain.history.FridgeItemHistoryRepository
 */
@Entity
@Table(
        name = "fridge_item_history",
        schema = "fridge_schema",
        indexes = {
                @Index(name = "idx_fih_fridge_item_id", columnList = "fridge_item_id, occurred_at"),
                @Index(name = "idx_fih_member_id",      columnList = "member_id, occurred_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FridgeItemHistory {

    @Id
    @Column(name = "history_id", nullable = false, updatable = false, length = 36)
    private String historyId;

    @Column(name = "fridge_item_id", nullable = false, updatable = false, length = 36)
    private String fridgeItemId;

    /**
     * 역정규화 — 이력 조회 시 Fridge JOIN 없이 회원 단위 집계 가능.
     */
    @Column(name = "member_id", nullable = false, updatable = false, length = 36)
    private String memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private HistoryEventType eventType;

    /**
     * 이벤트 유형별 세부 정보 (JSONB).
     * SqlTypes.JSON → SqlTypes.JSONB: PostgreSQL JSONB 타입과 매핑.
     * @JdbcTypeCode(SqlTypes.JSON)은 Hibernate가 json(CLOB)으로 매핑해
     * JSONB 컬럼과 타입 불일치가 발생하므로 JSONB로 직접 지정.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    // ── 팩토리 ────────────────────────────────────────────────────────

    /**
     * 기본 팩토리. payload는 Appender에서 ObjectMapper로 직렬화해 전달.
     */
    public static FridgeItemHistory of(
            String fridgeItemId,
            String memberId,
            HistoryEventType eventType,
            JsonNode payload,
            Instant occurredAt
    ) {
        FridgeItemHistory h = new FridgeItemHistory();
        h.historyId    = UUID.randomUUID().toString();
        h.fridgeItemId = Objects.requireNonNull(fridgeItemId, "fridgeItemId");
        h.memberId     = Objects.requireNonNull(memberId, "memberId");
        h.eventType    = Objects.requireNonNull(eventType, "eventType");
        h.payload      = payload;   // nullable 허용 (ADDED 등 세부 정보 없는 경우)
        h.occurredAt   = Objects.requireNonNull(occurredAt, "occurredAt");
        return h;
    }

    // ── 편의 쿼리 ─────────────────────────────────────────────────────

    /**
     * 이 이력이 개봉을 의미하는 이벤트인지 여부.
     * MOVED, PORTIONED, COOKED는 아이템이 실제로 접촉됐을 가능성이 높으므로 개봉으로 간주.
     */
    public boolean impliesOpened() {
        return eventType == HistoryEventType.MOVED
                || eventType == HistoryEventType.PORTIONED
                || eventType == HistoryEventType.COOKED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FridgeItemHistory h)) return false;
        return Objects.equals(historyId, h.historyId);
    }

    @Override
    public int hashCode() { return Objects.hash(historyId); }

    /**
     * 냉장고 아이템 이력 이벤트 유형.
     */
    public enum HistoryEventType {
        /** 냉장고에 추가됨 */
        ADDED,
        /** 보관 구역 이동 (payload: from, to) */
        MOVED,
        /** 소분됨 — 개봉 추정 (payload: portionCount) */
        PORTIONED,
        /** 요리 재료로 일부 사용됨 — 개봉 추정 (payload: cookedFridgeItemId) */
        COOKED,
        /** 소비기한 연장됨 (payload: additionalDays, newExpiresAt) */
        SHELF_EXTENDED
    }
}