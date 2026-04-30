package com.refridge.fridge_management.fridge.infrastructure.outbox;

import com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent;
import org.springframework.stereotype.Component;

/**
 * 도메인 이벤트 → Redis Stream Key / Aggregate ID 매핑 컴포넌트.
 *
 * <h2>역할</h2>
 * {@link FridgeOutboxAppender}에서 이벤트 타입별 stream key와
 * aggregate ID 추출 로직을 분리해 단일 책임을 유지한다.
 *
 * <h2>Outbox 제외 이벤트</h2>
 * {@link FridgeDomainEvent.FridgeItemExpirationRegisteredEvent}는
 * Outbox 발행 대상이 아니다 (감사/추적 목적에 한정).
 * {@link #isOutboxTarget(FridgeDomainEvent)}로 사전 확인한다.
 *
 * <h2>Stream Key 매핑표</h2>
 * <pre>
 * FridgeItemConsumedEvent          → fridge:consumed
 * FridgeItemDisposedEvent          → fridge:disposed
 * FridgeItemPortionedEvent         → fridge:portioned
 * FridgeItemCookedEvent            → fridge:cooked
 * FridgeItemNearExpiryEvent        → fridge:near-expiry
 * FridgeItemMovedEvent             → fridge:moved
 * FridgeItemShelfLifeExtendedEvent → fridge:extended
 * FridgeFillCompletedEvent         → fridge:fill-completed
 * FridgeItemExpirationRegistered   → (Outbox 제외)
 * </pre>
 *
 * @author 승훈
 * @since 2026-04-29
 */
@Component
public class FridgeDomainEventStreamKeyResolver {

    /**
     * 이벤트가 Outbox 발행 대상인지 여부.
     * {@code FridgeItemExpirationRegisteredEvent}는 발행 대상에서 제외한다.
     */
    public boolean isOutboxTarget(FridgeDomainEvent event) {
        return !(event instanceof FridgeDomainEvent.FridgeItemExpirationRegisteredEvent);
    }

    /**
     * 이벤트 → Redis Stream Key 매핑.
     *
     * @throws IllegalArgumentException 알 수 없는 이벤트 타입인 경우
     */
    public String resolveStreamKey(FridgeDomainEvent event) {
        return switch (event) {
            case FridgeDomainEvent.FridgeItemConsumedEvent ignored          -> "fridge:consumed";
            case FridgeDomainEvent.FridgeItemDisposedEvent ignored          -> "fridge:disposed";
            case FridgeDomainEvent.FridgeItemPortionedEvent ignored         -> "fridge:portioned";
            case FridgeDomainEvent.FridgeItemCookedEvent ignored            -> "fridge:cooked";
            case FridgeDomainEvent.FridgeItemNearExpiryEvent ignored        -> "fridge:near-expiry";
            case FridgeDomainEvent.FridgeItemMovedEvent ignored             -> "fridge:moved";
            case FridgeDomainEvent.FridgeItemShelfLifeExtendedEvent ignored -> "fridge:extended";
            case FridgeDomainEvent.FridgeFillCompletedEvent ignored         -> "fridge:fill-completed";
            default -> throw new IllegalArgumentException(
                    "Outbox 대상이 아닌 이벤트: " + event.getClass().getSimpleName());
        };
    }

    /**
     * 이벤트 → Aggregate ID (fridgeId) 추출.
     *
     * <h3>FridgeFillCompletedEvent</h3>
     * fridgeId를 직접 보유하지 않으므로 {@code fridgeItemId}를 aggregateId로 대체한다.
     * Outbox 테이블의 {@code aggregate_id}는 조회/감사 목적이므로 실용적 타협.
     */
    public String resolveAggregateId(FridgeDomainEvent event) {
        return switch (event) {
            case FridgeDomainEvent.FridgeItemConsumedEvent e          -> e.fridgeItemId();
            case FridgeDomainEvent.FridgeItemDisposedEvent e          -> e.fridgeItemId();
            case FridgeDomainEvent.FridgeItemPortionedEvent e         -> e.originalFridgeItemId();
            case FridgeDomainEvent.FridgeItemCookedEvent e            -> e.cookedFridgeItemId();
            case FridgeDomainEvent.FridgeItemNearExpiryEvent e        -> e.fridgeItemId();
            case FridgeDomainEvent.FridgeItemMovedEvent e             -> e.fridgeItemId();
            case FridgeDomainEvent.FridgeItemShelfLifeExtendedEvent e -> e.fridgeItemId();
            case FridgeDomainEvent.FridgeFillCompletedEvent e         -> e.fridgeItemId();
            default -> throw new IllegalArgumentException(
                    "Outbox 대상이 아닌 이벤트: " + event.getClass().getSimpleName());
        };
    }
}