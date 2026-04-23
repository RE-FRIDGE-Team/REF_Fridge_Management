package com.refridge.fridge_management.fridge.application.query;

import com.refridge.fridge_management.fridge.application.service.FridgeViewService;
import com.refridge.fridge_management.fridge.domain.FridgeSection;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;

import java.util.List;

/**
 * FridgeSection 조회 결과 DTO.
 * 구역별 아이템 목록과 집계값을 포함한다.
 *
 * @author 승훈
 * @since 2025-06-01
 * @see FridgeViewService
 */
public record FridgeSectionResult(
        SectionType sectionType,
        String sectionDisplayName,
        long activeItemCount,
        List<FridgeItemResult> items
) {
    public static FridgeSectionResult from(FridgeSection section) {
        List<FridgeItemResult> items = section.activeItems().stream()
                .map(FridgeItemResult::from)
                .toList();
        return new FridgeSectionResult(
                section.getSectionType(),
                section.getSectionType().getDisplayName(),
                items.size(),
                items
        );
    }
}
