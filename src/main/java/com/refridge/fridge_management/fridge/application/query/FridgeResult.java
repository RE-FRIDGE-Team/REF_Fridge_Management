package com.refridge.fridge_management.fridge.application.query;

import com.refridge.fridge_management.fridge.application.service.FridgeViewService;
import com.refridge.fridge_management.fridge.domain.Fridge;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 냉장고 전체 조회 결과 DTO.
 *
 * <h2>포함 정보</h2>
 * <ul>
 *   <li>냉장고 메타 (총가치, 총 아이템 수)</li>
 *   <li>구역별 아이템 목록 및 집계</li>
 *   <li>유통기한 임박 아이템 수 (UI 뱃지 표시용)</li>
 * </ul>
 *
 * <h2>CQRS 의도</h2>
 * 이 DTO는 읽기 전용 조회 결과다.
 * 쓰기 명령은 {@code ConsumeItemCommand}, {@code FillFridgeCommand} 등 별도 Command 객체를 사용.
 *
 * @author 승훈
 * @since 2025-06-01
 * @see FridgeViewService
 */
public record FridgeResult(
        String fridgeId,
        String memberId,
        BigDecimal totalValue,
        int totalActiveItemCount,
        long nearExpiryItemCount,       // 7일 이내 만료 아이템 수 (UI 상단 뱃지)
        Map<SectionType, FridgeSectionResult> sections
) {
    public static FridgeResult from(Fridge fridge) {
        Map<SectionType, FridgeSectionResult> sections = fridge.getSections().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> FridgeSectionResult.from(e.getValue())
                ));

        long nearExpiryCount = sections.values().stream()
                .flatMap(s -> s.items().stream())
                .filter(FridgeItemResult::nearExpiry)
                .count();

        return new FridgeResult(
                fridge.getFridgeId(),
                fridge.getMemberId(),
                fridge.getFridgeMeta().getTotalValue().getAmount(),
                fridge.getFridgeMeta().getActiveItemCount(),
                nearExpiryCount,
                sections
        );
    }
}
