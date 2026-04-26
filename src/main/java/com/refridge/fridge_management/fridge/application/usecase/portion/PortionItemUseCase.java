package com.refridge.fridge_management.fridge.application.usecase.portion;

import com.refridge.fridge_management.fridge.domain.Fridge;
import com.refridge.fridge_management.fridge.domain.FridgeItem;
import com.refridge.fridge_management.fridge.domain.repository.FridgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 소분 유스케이스 (UC5).
 *
 * <h2>처리 흐름</h2>
 * <pre>
 * POST /fridge/items/{id}/portion
 *   ↓
 * Fridge.portion(fridgeItemId, portionCount)
 *   → 원본: ACTIVE → PORTIONED_OUT (terminal)
 *   → FridgeMeta.totalValue -= 원본 가격
 *   → 자식 N개 생성 (수량·가격 비례 분할)
 *   → FridgeMeta.totalValue += 자식 가격 × N
 *   → FridgeItemPortionedEvent 등록
 *   ↓
 * FridgeOutboxAppender(BEFORE_COMMIT) → fridge_pending_event INSERT
 * </pre>
 *
 * <h2>소분 정책 검증</h2>
 * GroceryItemRef에 minPortionAmount가 있으면 도메인 레이어({@code Fridge.validatePortionPolicy})가
 * 소분 단위를 검증한다. 외부 어댑터(GroceryItemCatalogPort) 호출은 불필요하다.
 * (fill 시점에 이미 스냅샷으로 저장됨)
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Service
@RequiredArgsConstructor
public class PortionItemUseCase {

    private final FridgeRepository fridgeRepository;

    /**
     * 소분 실행.
     *
     * @param memberId     회원 ID
     * @param fridgeItemId 소분할 아이템 ID
     * @param portionCount 소분 수 (2 이상)
     * @return 생성된 자식 아이템 결과 목록
     * @throws IllegalArgumentException portionCount < 2, 소분 단위 정책 위반
     * @throws IllegalStateException    ACTIVE 상태가 아닌 아이템
     */
    @Transactional
    public List<PortionItemResult> execute(String memberId, String fridgeItemId, int portionCount) {
        Fridge fridge = requireFridge(memberId);
        List<FridgeItem> portionedItems = fridge.portion(fridgeItemId, portionCount);
        fridgeRepository.save(fridge);
        return PortionItemResult.fromList(portionedItems);
    }

    private Fridge requireFridge(String memberId) {
        return fridgeRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "냉장고를 찾을 수 없습니다. memberId=" + memberId));
    }
}