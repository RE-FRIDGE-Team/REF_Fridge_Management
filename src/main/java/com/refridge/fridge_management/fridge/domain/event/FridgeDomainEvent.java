package com.refridge.fridge_management.fridge.domain.event;

import com.refridge.fridge_management.fridge.domain.vo.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fridge BC 도메인 이벤트.
 * sealed interface + record 구현체(불변).
 *
 * AbstractAggregateRoot.registerEvent()로 등록,
 * Spring Data가 save() 후 ApplicationEventPublisher로 자동 발행.
 */
public sealed interface FridgeDomainEvent permits
        FridgeDomainEvent.FridgeItemConsumedEvent,
        FridgeDomainEvent.FridgeItemDisposedEvent,
        FridgeDomainEvent.FridgeItemPortionedEvent,
        FridgeDomainEvent.FridgeItemCookedEvent,
        FridgeDomainEvent.FridgeItemNearExpiryEvent,
        FridgeDomainEvent.FridgeItemMovedEvent,
        FridgeDomainEvent.FridgeItemShelfLifeExtendedEvent {

    String eventId();
    String memberId();
    Instant occurredAt();

    // ── 먹기 — Saving BC가 소비 이유(RAW/COOKED 등)에 따라 절약액 계산 ──
    record FridgeItemConsumedEvent(
            String eventId, String memberId, String fridgeItemId,
            GroceryItemRef groceryItemRef, Quantity consumedQuantity,
            Money purchasePrice, ItemProcessingType processingType, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemConsumedEvent of(String memberId, String fridgeItemId,
                                                 GroceryItemRef ref, Quantity qty, Money price, ItemProcessingType type) {
            return new FridgeItemConsumedEvent(
                    UUID.randomUUID().toString(), memberId, fridgeItemId, ref, qty, price, type, Instant.now());
        }
    }

    // ── 폐기 — Saving BC가 "낭비된 식비" 누적 ─────────────────────────
    record FridgeItemDisposedEvent(
            String eventId, String memberId, String fridgeItemId,
            GroceryItemRef groceryItemRef, Quantity disposedQuantity, Money lostPrice, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemDisposedEvent of(String memberId, String fridgeItemId,
                                                 GroceryItemRef ref, Quantity qty, Money lostPrice) {
            return new FridgeItemDisposedEvent(
                    UUID.randomUUID().toString(), memberId, fridgeItemId, ref, qty, lostPrice, Instant.now());
        }
    }

    // ── 소분 ──────────────────────────────────────────────────────────
    record FridgeItemPortionedEvent(
            String eventId, String memberId, String originalFridgeItemId,
            int portionCount, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemPortionedEvent of(String memberId, String originalId, int count) {
            return new FridgeItemPortionedEvent(
                    UUID.randomUUID().toString(), memberId, originalId, count, Instant.now());
        }
    }

    // ── 즉석 요리 — consumedItems 스냅샷 포함 (Saving BC 집계용) ──────
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

    // ── 유통기한 임박 — notification_server로 전달 ────────────────────
    record FridgeItemNearExpiryEvent(
            String eventId, String memberId, String fridgeItemId,
            GroceryItemRef groceryItemRef, LocalDate expiresAt,
            long daysUntilExpiry, SectionType section, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemNearExpiryEvent of(String memberId, String fridgeItemId,
                                                   GroceryItemRef ref, LocalDate expiresAt, long daysLeft, SectionType section) {
            return new FridgeItemNearExpiryEvent(
                    UUID.randomUUID().toString(), memberId, fridgeItemId,
                    ref, expiresAt, daysLeft, section, Instant.now());
        }
    }

    // ── 구역 이동 ─────────────────────────────────────────────────────
    record FridgeItemMovedEvent(
            String eventId, String memberId, String fridgeItemId,
            SectionType fromSection, SectionType toSection,
            boolean wasUpwardMove, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemMovedEvent of(String memberId, String fridgeItemId,
                                              SectionType from, SectionType to, boolean upward) {
            return new FridgeItemMovedEvent(
                    UUID.randomUUID().toString(), memberId, fridgeItemId, from, to, upward, Instant.now());
        }
    }

    // ── 기한 연장 ─────────────────────────────────────────────────────
    record FridgeItemShelfLifeExtendedEvent(
            String eventId, String memberId, String fridgeItemId,
            LocalDate originalExpiresAt, LocalDate newExpiresAt,
            int additionalDays, Instant occurredAt
    ) implements FridgeDomainEvent {
        public static FridgeItemShelfLifeExtendedEvent of(String memberId, String fridgeItemId,
                                                          LocalDate original, LocalDate newDate, int days) {
            return new FridgeItemShelfLifeExtendedEvent(
                    UUID.randomUUID().toString(), memberId, fridgeItemId,
                    original, newDate, days, Instant.now());
        }
    }

    /** 가공식품 처리 유형 (Saving BC 절약액 계산 분기용) */
    enum ItemProcessingType {
        RAW, COOKED, MEAL_KIT, RETORT, FROZEN, DELIVERY
    }
}
