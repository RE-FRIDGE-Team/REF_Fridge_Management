package com.refridge.fridge_management.fridge.infrastructure.history;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent;
import com.refridge.fridge_management.fridge.domain.history.FridgeItemHistory;
import com.refridge.fridge_management.fridge.domain.history.FridgeItemHistory.HistoryEventType;
import com.refridge.fridge_management.fridge.domain.history.FridgeItemHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * 냉장고 아이템 이력 기록기.
 *
 * <h2>역할</h2>
 * 도메인 이벤트를 재활용해 {@code fridge_item_history} 테이블에 이력을 INSERT한다.
 * 도메인 레이어를 전혀 수정하지 않고 인프라 레이어에서만 이력을 추가하므로 OCP를 만족한다.
 *
 * <h2>기록 대상 이벤트</h2>
 * <ul>
 *   <li>{@code FridgeFillCompletedEvent}        → ADDED</li>
 *   <li>{@code FridgeItemMovedEvent}             → MOVED</li>
 *   <li>{@code FridgeItemPortionedEvent}         → PORTIONED</li>
 *   <li>{@code FridgeItemCookedEvent}            → COOKED (재료 아이템별)</li>
 *   <li>{@code FridgeItemShelfLifeExtendedEvent} → SHELF_EXTENDED</li>
 * </ul>
 *
 * <h2>트랜잭션</h2>
 * {@code BEFORE_COMMIT} — 비즈니스 트랜잭션과 동일한 트랜잭션에서 INSERT되므로 원자성 보장.
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FridgeItemHistoryAppender {

    private final FridgeItemHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;   // tools.jackson.databind.ObjectMapper

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onFridgeFillCompleted(FridgeDomainEvent.FridgeFillCompletedEvent event) {
        ObjectNode payload = objectMapper.createObjectNode();
        save(event.fridgeItemId(), event.memberId(),
                HistoryEventType.ADDED, payload, event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onFridgeItemMoved(FridgeDomainEvent.FridgeItemMovedEvent event) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("from", event.fromSection().name());
        payload.put("to",   event.toSection().name());
        payload.put("wasUpwardMove", event.wasUpwardMove());
        save(event.fridgeItemId(), event.memberId(),
                HistoryEventType.MOVED, payload, event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onFridgeItemPortioned(FridgeDomainEvent.FridgeItemPortionedEvent event) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("portionCount", event.portionCount());
        save(event.originalFridgeItemId(), event.memberId(),
                HistoryEventType.PORTIONED, payload, event.occurredAt());
    }

    /**
     * 요리 이벤트 — 재료로 사용된 각 FridgeItem마다 COOKED 이력을 기록한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onFridgeItemCooked(FridgeDomainEvent.FridgeItemCookedEvent event) {
        for (FridgeDomainEvent.ConsumedIngredient ingredient : event.consumedIngredients()) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("cookedFridgeItemId", event.cookedFridgeItemId());
            save(ingredient.fridgeItemId(), event.memberId(),
                    HistoryEventType.COOKED, payload, event.occurredAt());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onFridgeItemShelfLifeExtended(
            FridgeDomainEvent.FridgeItemShelfLifeExtendedEvent event) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("additionalDays", event.additionalDays());
        payload.put("newExpiresAt",   event.newExpiresAt().toString());
        payload.put("extensionCount", event.extensionCount());
        save(event.fridgeItemId(), event.memberId(),
                HistoryEventType.SHELF_EXTENDED, payload, event.occurredAt());
    }

    private void save(String fridgeItemId, String memberId,
                      HistoryEventType eventType, ObjectNode payload, Instant occurredAt) {
        try {
            historyRepository.save(
                    FridgeItemHistory.of(fridgeItemId, memberId, eventType, payload, occurredAt));
        } catch (Exception e) {
            log.error("[FridgeItemHistoryAppender] 이력 기록 실패. fridgeItemId={}, eventType={}",
                    fridgeItemId, eventType, e);
            throw e;
        }
    }
}