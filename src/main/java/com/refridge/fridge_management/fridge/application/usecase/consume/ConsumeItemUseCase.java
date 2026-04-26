package com.refridge.fridge_management.fridge.application.usecase.consume;

import com.refridge.fridge_management.fridge.domain.Fridge;
import com.refridge.fridge_management.fridge.domain.repository.FridgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 먹기 유스케이스 (UC3).
 *
 * <h2>처리 흐름</h2>
 * <pre>
 * POST /fridge/items/{id}/consume
 *   ↓
 * Fridge.consume(fridgeItemId)
 *   → FridgeItem.status: ACTIVE → CONSUMED
 *   → FridgeMeta.totalValue -= purchasePrice
 *   → FridgeItemConsumedEvent 등록
 *   ↓
 * FridgeOutboxAppender(BEFORE_COMMIT) → fridge_pending_event INSERT
 *   ↓  (비동기)
 * FridgeOutboxRelayer → fridge:consumed XADD
 *   ↓
 * Saving BC consumer
 *   → processingType=RAW: 절약액 0
 *   → processingType∈{COOKED, MEAL_KIT, RETORT, FROZEN, DELIVERY}: 배달가와 비교
 * </pre>
 *
 * <h2>냉장고 조회 전략</h2>
 * 먹기·폐기·이동 등 단순 아이템 조작은 items를 함께 로딩할 필요가 없으므로
 * {@code findByMemberId}(sections EAGER, items LAZY)를 사용한다.
 * Fridge.consume() 내부에서 sections를 순회해 대상 아이템을 찾을 때
 * @BatchSize(20) 덕분에 N+1 없이 처리된다.
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Service
@RequiredArgsConstructor
public class ConsumeItemUseCase {

    private final FridgeRepository fridgeRepository;

    /**
     * 아이템 먹기.
     *
     * @param memberId     회원 ID
     * @param fridgeItemId 먹을 아이템 ID
     * @throws IllegalArgumentException 냉장고가 없거나 ACTIVE 아이템이 없는 경우
     */
    @Transactional
    public void execute(String memberId, String fridgeItemId) {
        Fridge fridge = requireFridge(memberId);
        fridge.consume(fridgeItemId);
        fridgeRepository.save(fridge);
    }

    private Fridge requireFridge(String memberId) {
        return fridgeRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "냉장고를 찾을 수 없습니다. memberId=" + memberId));
    }
}