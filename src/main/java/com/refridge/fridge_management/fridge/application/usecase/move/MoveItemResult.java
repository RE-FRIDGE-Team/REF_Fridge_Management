package com.refridge.fridge_management.fridge.application.usecase.move;

import com.refridge.fridge_management.fridge.domain.vo.SectionType;

/**
 * 구역 이동 결과 DTO.
 *
 * <h2>wasUpwardMove</h2>
 * 냉동→냉장, 냉동→상온, 냉장→상온 방향(온도 상승)이면 true.
 * 클라이언트가 이 값을 보고 "유통기한이 빨리 지날 수 있습니다" 경고를 표시한다.
 *
 * @param fridgeItemId  이동된 아이템 ID
 * @param fromSection   이동 전 구역
 * @param toSection     이동 후 구역
 * @param wasUpwardMove 상행성 이동(온도 상승) 여부
 *
 * @author 승훈
 * @since 2026-04-26
 */
public record MoveItemResult(
        String fridgeItemId,
        SectionType fromSection,
        SectionType toSection,
        boolean wasUpwardMove
) {}