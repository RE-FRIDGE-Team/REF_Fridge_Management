package com.refridge.fridge_management.fridge.domain.event;

import com.refridge.fridge_management.fridge.domain.vo.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fridge BC 도메인 이벤트.
 * sealed interface + record 구현체(불변).
 *
 * <h2>발행 메커니즘</h2>
 * {@code AbstractAggregateRoot.registerEvent()}로 등록,
 * Spring Data가 {@code FridgeRepository.save()} 직후
 * {@code ApplicationEventPublisher}로 자동 발행한다.
 *
 * <h2>Outbox 흐름</h2>
 * {@code FridgeOutboxAppender}가 {@code @TransactionalEventListener(BEFORE_COMMIT)}로
 * 이 이벤트들을 캡처해 {@code fridge_pending_event} 테이블에 INSERT한다.
 * {@code FridgeOutboxRelayer}(@Scheduled)가 Redis Stream(XADD)으로 발행.
 *
 * <h2>이벤트 카탈로그</h2>
 * <pre>
 * FridgeItemConsumedEvent       → fridge:consumed       → Saving BC
 * FridgeItemDisposedEvent       → fridge:disposed       → Saving BC
 * FridgeItemPortionedEvent      → fridge:portioned      → (future) Statistics
 * FridgeItemCookedEvent         → fridge:cooked         → Saving BC
 * FridgeItemNearExpiryEvent     → fridge:near-expiry    → noti_server
 * FridgeItemMovedEvent          → fridge:moved          → (future) Statistics
 * FridgeItemShelfLifeExtendedEvent → fridge:extended    → (future) Statistics
 * FridgeFillCompletedEvent      → fridge:fill-completed → Feedback BC (core_server)
 * </pre>
 *
 * @author 승훈
 * @since 2025-06-01
 * @see com.refridge.fridge_management.fridge.domain.Fridge
 */
public sealed interface FridgeDomainEvent permits
        FridgeDomainEvent.FridgeItemConsumedEvent,
        FridgeDomainEvent.FridgeItemDisposedEvent,
        FridgeDomainEvent.FridgeItemPortionedEvent,
        FridgeDomainEvent.FridgeItemCookedEvent,
        FridgeDomainEvent.FridgeItemNearExpiryEvent,
        FridgeDomainEvent.FridgeItemMovedEvent,
        FridgeDomainEvent.FridgeItemShelfLifeExtendedEvent,
        FridgeDomainEvent.FridgeFillCompletedEvent {

    String eventId();
    String memberId();
    Instant occurredAt();

    // ── 먹기 — Saving BC가 소비 유형(RAW/COOKED 등)에 따라 절약액 계산 ─
    record FridgeItemConsumedEvent(
            String eventId, String memberId, String fridgeItemId,
            GroceryItemRef groceryItemRef, Quantity consumedQuantity,
            Money purchasePrice, ItemProcessingType processingType, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemConsumedEvent of(String memberId, String fridgeItemId,
                                                 GroceryItemRef ref, Quantity qty, Money price,
                                                 ItemProcessingType type) {
            return new FridgeItemConsumedEvent(
                    UUID.randomUUID().toString(), memberId, fridgeItemId,
                    ref, qty, price, type, Instant.now());
        }
    }

    // ── 폐기 — Saving BC가 "낭비된 식비" 누적 ────────────────────────
    record FridgeItemDisposedEvent(
            String eventId, String memberId, String fridgeItemId,
            GroceryItemRef groceryItemRef, Quantity disposedQuantity,
            Money lostPrice, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemDisposedEvent of(String memberId, String fridgeItemId,
                                                 GroceryItemRef ref, Quantity qty, Money lostPrice) {
            return new FridgeItemDisposedEvent(
                    UUID.randomUUID().toString(), memberId, fridgeItemId,
                    ref, qty, lostPrice, Instant.now());
        }
    }

    // ── 소분 ─────────────────────────────────────────────────────────
    record FridgeItemPortionedEvent(
            String eventId, String memberId, String originalFridgeItemId,
            int portionCount, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemPortionedEvent of(String memberId, String originalId, int count) {
            return new FridgeItemPortionedEvent(
                    UUID.randomUUID().toString(), memberId, originalId, count, Instant.now());
        }
    }

    // ── 즉석 요리 — consumedItems 스냅샷 포함 (Saving BC 집계용) ─────
    record FridgeItemCookedEvent(
            String eventId, String memberId, String cookedFridgeItemId,
            List<ConsumedIngredient> consumedIngredients, int servings, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemCookedEvent of(String memberId, String cookedItemId,
                                               List<ConsumedIngredient> ingredients, int servings) {
            return new FridgeItemCookedEvent(
                    UUID.randomUUID().toString(), memberId, cookedItemId,
                    List.copyOf(ingredients), servings, Instant.now());
        }
    }

    /** 요리 재료 스냅샷 (Saving BC가 재료별 절약액 계산 가능하도록) */
    record ConsumedIngredient(String fridgeItemId, GroceryItemRef groceryItemRef, Money price) {}

    // ── 유통기한 임박 — notification_server로 전달 ───────────────────
    record FridgeItemNearExpiryEvent(
            String eventId, String memberId, String fridgeItemId,
            GroceryItemRef groceryItemRef, LocalDate expiresAt,
            long daysUntilExpiry, SectionType section, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemNearExpiryEvent of(String memberId, String fridgeItemId,
                                                   GroceryItemRef ref, LocalDate expiresAt,
                                                   long daysLeft, SectionType section) {
            return new FridgeItemNearExpiryEvent(
                    UUID.randomUUID().toString(), memberId, fridgeItemId,
                    ref, expiresAt, daysLeft, section, Instant.now());
        }
    }

    // ── 구역 이동 ────────────────────────────────────────────────────
    record FridgeItemMovedEvent(
            String eventId, String memberId, String fridgeItemId,
            SectionType fromSection, SectionType toSection,
            boolean wasUpwardMove, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemMovedEvent of(String memberId, String fridgeItemId,
                                              SectionType from, SectionType to, boolean upward) {
            return new FridgeItemMovedEvent(
                    UUID.randomUUID().toString(), memberId, fridgeItemId,
                    from, to, upward, Instant.now());
        }
    }

    // ── 기한 연장 ────────────────────────────────────────────────────
    record FridgeItemShelfLifeExtendedEvent(
            String eventId, String memberId, String fridgeItemId,
            LocalDate originalExpiresAt, LocalDate newExpiresAt,
            int additionalDays, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemShelfLifeExtendedEvent of(String memberId, String fridgeItemId,
                                                          LocalDate original, LocalDate newDate,
                                                          int days) {
            return new FridgeItemShelfLifeExtendedEvent(
                    UUID.randomUUID().toString(), memberId, fridgeItemId,
                    original, newDate, days, Instant.now());
        }
    }

    // ── 냉장고 채우기 완료 — Feedback BC(core_server)가 구독 ─────────
    /**
     * 냉장고 채우기 완료 이벤트 (신규).
     *
     * <h2>역할</h2>
     * Flutter 클라이언트가 {@code POST /fridge/items}를 호출해 인식 결과를 확정·저장한 뒤
     * fridge_server가 이 이벤트를 발행한다.
     * Outbox → {@code fridge:fill-completed} Redis Stream →
     * core_server의 {@code REFFillEventConsumer}가 수신해
     * diff(originalSnapshot, finalEdited)를 계산하고
     * {@code approveFeedback()} 또는 {@code correctFeedback()}을 호출한다.
     *
     * <h2>userEditedFields 설계 의도</h2>
     * Feedback BC가 이 필드만 보고도 빠르게 분기 가능:
     * <ul>
     *   <li>비어있으면 → {@code approveFeedback(price)} (수정 없음, 긍정 피드백)</li>
     *   <li>비어있지 않으면 → {@code correctFeedback(REFFeedbackCorrectCommand)} (부정 피드백)</li>
     * </ul>
     * 단, Feedback BC가 신뢰성 검증을 위해 원본 스냅샷과 한 번 더 비교하는 것을 권장한다.
     *
     * <h2>recognitionId</h2>
     * Feedback BC가 {@code REFRecognitionFeedback}을 조회하는 키.
     * Recognition 결과가 없었던 경우(직접 입력) {@code null}이 될 수 있다.
     * Feedback BC의 {@code findOrCreate(recognitionId)} 멱등 패턴으로 안전하게 처리된다.
     *
     * @param eventId             이벤트 고유 식별자 (UUID)
     * @param memberId            회원 ID
     * @param recognitionId       Recognition AR ID (nullable — 직접 입력 시 null)
     * @param fridgeItemId        생성된 FridgeItem ID
     * @param finalGroceryItemRef 사용자가 최종 확정한 식재료 스냅샷
     * @param finalBrandName      사용자 입력 브랜드명 (nullable)
     * @param finalQuantity       최종 확정 수량
     * @param purchasePrice       구매 가격
     * @param userEditedFields    사용자가 수정한 필드 집합 (수정 없으면 빈 Set)
     * @param occurredAt          이벤트 발생 시각
     */
    record FridgeFillCompletedEvent(
            String eventId,
            String memberId,
            UUID recognitionId,
            String fridgeItemId,
            GroceryItemRef finalGroceryItemRef,
            String finalBrandName,
            Quantity finalQuantity,
            Money purchasePrice,
            Set<UserEditedField> userEditedFields,
            Instant occurredAt
    ) implements FridgeDomainEvent {

        /**
         * 팩토리 메서드.
         *
         * @param memberId            회원 ID
         * @param recognitionId       Recognition AR ID (nullable)
         * @param fridgeItemId        생성된 FridgeItem ID
         * @param finalGroceryItemRef 최종 확정 식재료 스냅샷
         * @param finalBrandName      최종 브랜드명 (nullable)
         * @param finalQuantity       최종 수량
         * @param purchasePrice       구매 가격
         * @param userEditedFields    수정 필드 집합 (불변 복사본으로 저장)
         */
        public static FridgeFillCompletedEvent of(
                String memberId,
                UUID recognitionId,
                String fridgeItemId,
                GroceryItemRef finalGroceryItemRef,
                String finalBrandName,
                Quantity finalQuantity,
                Money purchasePrice,
                Set<UserEditedField> userEditedFields
        ) {
            return new FridgeFillCompletedEvent(
                    UUID.randomUUID().toString(),
                    memberId,
                    recognitionId,
                    fridgeItemId,
                    finalGroceryItemRef,
                    finalBrandName,
                    finalQuantity,
                    purchasePrice,
                    Set.copyOf(userEditedFields),   // 불변 복사본
                    Instant.now()
            );
        }

        /**
         * 수정 없이 인식 결과를 그대로 확정한 경우 (긍정 피드백 fast-path).
         * userEditedFields가 빈 Set이므로 Feedback BC가 즉시 approveFeedback()으로 분기한다.
         */
        public boolean hasNoEdits() {
            return userEditedFields.isEmpty();
        }
    }

    // ── 공통 ─────────────────────────────────────────────────────────

    /** 가공식품 처리 유형 (Saving BC 절약액 계산 분기용) */
    enum ItemProcessingType {
        RAW,        // 비가공 단순 원재료 (쌀, 오렌지 등) — 절약액 0
        COOKED,     // 즉석 요리 결과
        MEAL_KIT,   // 밀키트
        RETORT,     // 레토르트
        FROZEN,     // 냉동식품
        DELIVERY    // 배달음식 형태로 구매한 것
    }
}