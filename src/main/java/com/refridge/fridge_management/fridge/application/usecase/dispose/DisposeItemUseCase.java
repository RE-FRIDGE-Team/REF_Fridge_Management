package com.refridge.fridge_management.fridge.application.usecase.dispose;

import com.refridge.fridge_management.fridge.domain.Fridge;
import com.refridge.fridge_management.fridge.domain.repository.FridgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 폐기 유스케이스 (UC6).
 *
 * <h2>처리 흐름</h2>
 * <pre>
 * POST /fridge/items/{id}/dispose
 *   ↓
 * Fridge.dispose(fridgeItemId)
 *   → FridgeItem.status: ACTIVE → DISPOSED
 *   → FridgeMeta.totalValue -= purchasePrice
 *   → FridgeItemDisposedEvent 등록 (lostPrice 포함)
 *   ↓
 * FridgeOutboxAppender(BEFORE_COMMIT) → fridge_pending_event INSERT
 *   ↓  (비동기)
 * FridgeOutboxRelayer → fridge:disposed XADD
 *   ↓
 * Saving BC consumer → "낭비된 식비" 누적
 * </pre>
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Service
@RequiredArgsConstructor
public class DisposeItemUseCase {

    private final FridgeRepository fridgeRepository;

    /**
     * 아이템 폐기.
     *
     * @param memberId     회원 ID
     * @param fridgeItemId 폐기할 아이템 ID
     * @throws IllegalArgumentException 냉장고가 없거나 ACTIVE 아이템이 없는 경우
     */
    @Transactional
    public void execute(String memberId, String fridgeItemId) {
        Fridge fridge = requireFridge(memberId);
        fridge.dispose(fridgeItemId);
        fridgeRepository.save(fridge);
    }

    private Fridge requireFridge(String memberId) {
        return fridgeRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "냉장고를 찾을 수 없습니다. memberId=" + memberId));
    }
}