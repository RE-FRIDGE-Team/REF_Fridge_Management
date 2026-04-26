package com.refridge.fridge_management.fridge.application.usecase.move;

import com.refridge.fridge_management.fridge.domain.Fridge;
import com.refridge.fridge_management.fridge.domain.repository.FridgeRepository;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구역 이동 유스케이스 (UC4).
 *
 * <h2>처리 흐름</h2>
 * <pre>
 * PATCH /fridge/items/{id}/section
 *   ↓
 * Fridge.move(fridgeItemId, targetSection)
 *   → UpwardMoveWarningPolicy 검증
 *   → FridgeItem.sectionType 변경 (status: ACTIVE 유지)
 *   → FridgeItemMovedEvent 등록 (wasUpwardMove 포함)
 *   ↓
 * FridgeOutboxAppender(BEFORE_COMMIT) → fridge_pending_event INSERT
 *   ↓  (비동기)
 * FridgeOutboxRelayer → fridge:moved XADD
 *   ↓
 * (future) Statistics BC consumer
 * </pre>
 *
 * <h2>상행성 이동 경고</h2>
 * 냉동→냉장, 냉동→상온, 냉장→상온은 {@code wasUpwardMove=true}로 이벤트가 발행된다.
 * 도메인 레이어에서 이미 검증하므로 유스케이스는 결과만 DTO로 변환해 반환한다.
 * 클라이언트가 경고를 사전 표시하고 사용자가 확인 후 호출하는 흐름이므로
 * 상행성 이동 자체를 막지는 않는다.
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Service
@RequiredArgsConstructor
public class MoveItemUseCase {

    private final FridgeRepository fridgeRepository;

    /**
     * 구역 이동 실행.
     *
     * @param memberId      회원 ID
     * @param fridgeItemId  이동할 아이템 ID
     * @param targetSection 목적지 구역
     * @return 이동 결과 (상행성 경고 여부 포함)
     * @throws IllegalArgumentException 냉장고가 없거나 동일 구역으로 이동 시도
     * @throws IllegalStateException    ACTIVE 상태가 아닌 아이템 조작 시도
     */
    @Transactional
    public MoveItemResult execute(String memberId, String fridgeItemId, SectionType targetSection) {
        Fridge fridge = requireFridge(memberId);

        // 이동 전 구역 기록 (이벤트에서 from 값 추출용)
        SectionType fromSection = fridge.getSections().values().stream()
                .flatMap(s -> s.allItems().stream())
                .filter(i -> i.getFridgeItemId().equals(fridgeItemId))
                .map(i -> i.getSectionType())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "아이템을 찾을 수 없습니다. fridgeItemId=" + fridgeItemId));

        fridge.move(fridgeItemId, targetSection);
        fridgeRepository.save(fridge);

        boolean wasUpwardMove = fromSection.isUpwardMoveTo(targetSection);
        return new MoveItemResult(fridgeItemId, fromSection, targetSection, wasUpwardMove);
    }

    private Fridge requireFridge(String memberId) {
        return fridgeRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "냉장고를 찾을 수 없습니다. memberId=" + memberId));
    }
}