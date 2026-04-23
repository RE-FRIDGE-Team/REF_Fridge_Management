package com.refridge.fridge_management.fridge.application.query;

import com.refridge.fridge_management.fridge.application.service.FridgeItemSearchService;
import com.refridge.fridge_management.fridge.domain.vo.ItemStatus;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;
import lombok.Builder;

/**
 * 냉장고 아이템 검색 조건 Query 객체.
 *
 * <h2>네이밍 컨벤션</h2>
 * 읽기 요청 파라미터는 Command 대신 SearchCondition 접미사를 사용한다.
 * Command는 상태 변경 의도를 가지는 쓰기 명령에만 사용.
 *
 * @author 승훈
 * @since 2025-06-01
 * @see FridgeItemSearchService
 */
@Builder
public record FridgeItemSearchCondition(
        String fridgeId,
        SectionType sectionType,     // null이면 전체 구역
        ItemStatus status,           // null이면 ACTIVE만 조회 (기본값)
        String keyword,              // 식품명 검색 (null이면 전체)
        int page,
        int size,
        String sortBy,               // "expiresAt" | "purchasePrice" | "groceryItemName"
        String sortDir               // "asc" | "desc"
) {
    public static FridgeItemSearchCondition of(
            String fridgeId, SectionType sectionType, ItemStatus status,
            String keyword, int page, int size, String sortBy, String sortDir
    ) {
        return new FridgeItemSearchCondition(
                fridgeId, sectionType, status, keyword, page, size,
                sortBy != null ? sortBy : "expiresAt",
                sortDir != null ? sortDir : "asc"
        );
    }
}
