package com.refridge.fridge_management.fridge.application.usecase.fill;

import com.refridge.fridge_management.fridge.domain.FridgeItem;
import com.refridge.fridge_management.fridge.domain.vo.GroceryItemRef.FoodCategory;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 냉장고 채우기 단건 결과 DTO.
 *
 * <h2>역할</h2>
 * {@code FillFridgeUseCase}가 반환하는 불변 결과 레코드.
 * 컨트롤러가 이를 {@code List<FillFridgeResult>}로 받아
 * HTTP 200 응답으로 직렬화한다.
 *
 * <h2>포함 정보</h2>
 * 클라이언트가 채우기 직후 냉장고 목록을 새로 조회하지 않고도
 * 추가된 아이템을 즉시 화면에 표시할 수 있도록 필요한 최소 정보를 담는다.
 *
 * @param fridgeItemId   생성된 FridgeItem ID
 * @param groceryItemId  식재료 ID
 * @param groceryName    식재료명
 * @param category       식품 카테고리
 * @param sectionType    보관 구역
 * @param quantityAmount 수량 값
 * @param quantityUnit   수량 단위
 * @param purchasePrice  구매 가격
 * @param expiresAt      유통기한
 *
 * @author 승훈
 * @since 2026-04-26
 * @see FillFridgeUseCase
 */
public record FillFridgeResult(
        String fridgeItemId,
        String groceryItemId,
        String groceryName,
        FoodCategory category,
        SectionType sectionType,
        BigDecimal quantityAmount,
        String quantityUnit,
        BigDecimal purchasePrice,
        LocalDate expiresAt
) {
    /**
     * FridgeItem 엔티티 → 결과 DTO 변환 팩토리.
     */
    public static FillFridgeResult from(FridgeItem item) {
        return new FillFridgeResult(
                item.getFridgeItemId(),
                item.getGroceryItemRef().getGroceryItemId(),
                item.getGroceryItemRef().getName(),
                item.getGroceryItemRef().getCategory(),
                item.getSectionType(),
                item.getQuantity().getAmount(),
                item.getQuantity().getUnit().name(),
                item.getPurchasePrice().getAmount(),
                item.getExpirationInfo().getExpiresAt()
        );
    }
}