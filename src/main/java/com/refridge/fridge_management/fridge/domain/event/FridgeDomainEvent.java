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
 * {@code FridgeItemHistoryAppender}도 동일한 BEFORE_COMMIT에서 이력 테이블에 INSERT.
 *
 * <h2>이벤트 카탈로그</h2>
 * <pre>
 * FridgeItemConsumedEvent             → fridge:consumed       → Saving BC
 * FridgeItemDisposedEvent             → fridge:disposed       → Saving BC
 * FridgeItemPortionedEvent            → fridge:portioned      → (future) Statistics
 * FridgeItemCookedEvent               → fridge:cooked         → Saving BC
 * FridgeItemNearExpiryEvent           → fridge:near-expiry    → noti_server
 * FridgeItemMovedEvent                → fridge:moved          → (future) Statistics
 * FridgeItemShelfLifeExtendedEvent    → fridge:extended       → (future) Statistics
 * FridgeFillCompletedEvent            → fridge:fill-completed → Feedback BC (core_server)
 * FridgeItemExpirationRegisteredEvent → Outbox 미발행, 이력 기록 전용
 * </pre>
 *
 * @author 승훈
 * @since 2025-06-01
 */
public sealed interface FridgeDomainEvent permits
        FridgeDomainEvent.FridgeItemConsumedEvent,
        FridgeDomainEvent.FridgeItemDisposedEvent,
        FridgeDomainEvent.FridgeItemPortionedEvent,
        FridgeDomainEvent.FridgeItemCookedEvent,
        FridgeDomainEvent.FridgeItemNearExpiryEvent,
        FridgeDomainEvent.FridgeItemMovedEvent,
        FridgeDomainEvent.FridgeItemShelfLifeExtendedEvent,
        FridgeDomainEvent.FridgeFillCompletedEvent,
        FridgeDomainEvent.FridgeItemExpirationRegisteredEvent {

    String eventId();
    String memberId();
    Instant occurredAt();

    // ── 먹기 ─────────────────────────────────────────────────────────
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

    // ── 폐기 ─────────────────────────────────────────────────────────
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

    // ── 즉석 요리 ────────────────────────────────────────────────────
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

    record ConsumedIngredient(String fridgeItemId, GroceryItemRef groceryItemRef, Money price) {}

    // ── 소비기한 임박 ────────────────────────────────────────────────
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

    // ── 소비기한 연장 ────────────────────────────────────────────────
    /**
     * @param extensionCount 연장 후 누적 연장 횟수 (1 이상).
     *                       이력({@code SHELF_EXTENDED}) 및 향후 Statistics BC 집계에 활용.
     */
    record FridgeItemShelfLifeExtendedEvent(
            String eventId, String memberId, String fridgeItemId,
            LocalDate originalExpiresAt, LocalDate newExpiresAt,
            int additionalDays, int extensionCount, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemShelfLifeExtendedEvent of(
                String memberId, String fridgeItemId,
                LocalDate original, LocalDate newDate,
                int days, int extensionCount) {
            return new FridgeItemShelfLifeExtendedEvent(
                    UUID.randomUUID().toString(), memberId, fridgeItemId,
                    original, newDate, days, extensionCount, Instant.now());
        }
    }

    // ── 냉장고 채우기 완료 ───────────────────────────────────────────
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
        public static FridgeFillCompletedEvent of(
                String memberId, UUID recognitionId, String fridgeItemId,
                GroceryItemRef finalGroceryItemRef, String finalBrandName,
                Quantity finalQuantity, Money purchasePrice,
                Set<UserEditedField> userEditedFields) {
            return new FridgeFillCompletedEvent(
                    UUID.randomUUID().toString(), memberId, recognitionId,
                    fridgeItemId, finalGroceryItemRef, finalBrandName,
                    finalQuantity, purchasePrice, Set.copyOf(userEditedFields), Instant.now());
        }

        public boolean hasNoEdits() { return userEditedFields.isEmpty(); }
    }

    // ── 소비기한 등록 (채우기 이후 별도 등록) ───────────────────────
    /**
     * 소비기한 등록 이벤트.
     * Outbox 발행 대상이 아님. 이력 기록 및 감사 목적.
     */
    record FridgeItemExpirationRegisteredEvent(
            String eventId, String memberId, String fridgeItemId,
            LocalDate expiresAt, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemExpirationRegisteredEvent of(
                String memberId, String fridgeItemId, LocalDate expiresAt) {
            return new FridgeItemExpirationRegisteredEvent(
                    UUID.randomUUID().toString(), memberId, fridgeItemId,
                    expiresAt, Instant.now());
        }
    }

    // ── 공통 ─────────────────────────────────────────────────────────
    enum ItemProcessingType {
        RAW, COOKED, MEAL_KIT, RETORT, FROZEN, DELIVERY
    }
}