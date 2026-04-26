package com.refridge.fridge_management.fridge.application.usecase.portion;

import com.refridge.fridge_management.fridge.domain.FridgeItem;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 소분 결과 DTO.
 *
 * <h2>역할</h2>
 * {@code PortionItemUseCase}가 반환하는 불변 결과 레코드.
 * 소분으로 생성된 자식 아이템 N개 목록을 담아 클라이언트에 반환한다.
 * 클라이언트는 이 결과로 즉시 화면을 갱신할 수 있다.
 *
 * @param fridgeItemId   생성된 자식 FridgeItem ID
 * @param groceryName    식재료명 (부모와 동일)
 * @param sectionType    보관 구역 (부모와 동일)
 * @param quantityAmount 소분 후 수량 (부모 수량 / portionCount)
 * @param quantityUnit   수량 단위
 * @param purchasePrice  소분 후 가격 (비례 분할)
 * @param expiresAt      유통기한 (부모와 동일)
 *
 * @author 승훈
 * @since 2026-04-26
 */
public record PortionItemResult(
        String fridgeItemId,
        String groceryName,
        SectionType sectionType,
        BigDecimal quantityAmount,
        String quantityUnit,
        BigDecimal purchasePrice,
        LocalDate expiresAt
) {
    public static PortionItemResult from(FridgeItem item) {
        return new PortionItemResult(
                item.getFridgeItemId(),
                item.getGroceryItemRef().getName(),
                item.getSectionType(),
                item.getQuantity().getAmount(),
                item.getQuantity().getUnit().name(),
                item.getPurchasePrice().getAmount(),
                item.getExpirationInfo().getExpiresAt()
        );
    }

    public static List<PortionItemResult> fromList(List<FridgeItem> items) {
        return items.stream().map(PortionItemResult::from).toList();
    }
}