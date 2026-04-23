package com.refridge.fridge_management.fridge.application.query;

import com.refridge.fridge_management.fridge.application.service.FridgeViewService;
import com.refridge.fridge_management.fridge.domain.FridgeItem;
import com.refridge.fridge_management.fridge.domain.vo.GroceryItemRef.FoodCategory;
import com.refridge.fridge_management.fridge.domain.vo.ItemStatus;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * FridgeItem 단건 조회 결과 DTO.
 *
 * <h2>record 선택 이유</h2>
 * 조회 결과는 불변이므로 record가 적합.
 * equals/hashCode/toString이 자동 생성되어 테스트에 유리.
 *
 * <h2>nearExpiry 플래그</h2>
 * 7일 이내 만료 여부를 서버에서 계산해 내려줌.
 * 클라이언트가 날짜 비교 로직을 갖지 않도록 캡슐화.
 *
 * @author 승훈
 * @since 2025-06-01
 * @see FridgeViewService
 */
public record FridgeItemResult(
        String fridgeItemId,
        String groceryItemId,
        String groceryItemName,
        FoodCategory category,
        SectionType sectionType,
        ItemStatus status,
        BigDecimal quantityAmount,
        String quantityUnit,
        BigDecimal purchasePrice,
        LocalDate expiresAt,
        boolean nearExpiry,          // 7일 이내 만료 여부 (서버 계산)
        boolean shelfLifeExtended,   // 유통기한 연장 이력
        String parentFridgeItemId    // 소분 자식인 경우 원본 ID (nullable)
) {
    private static final int NEAR_EXPIRY_THRESHOLD_DAYS = 7;

    /**
     * FridgeItem 엔티티 → DTO 변환 팩토리.
     * 오늘 날짜를 기준으로 nearExpiry 플래그를 계산한다.
     */
    public static FridgeItemResult from(FridgeItem item) {
        LocalDate today = LocalDate.now();
        return new FridgeItemResult(
                item.getFridgeItemId(),
                item.getGroceryItemRef().getGroceryItemId(),
                item.getGroceryItemRef().getName(),
                item.getGroceryItemRef().getCategory(),
                item.getSectionType(),
                item.getStatus(),
                item.getQuantity().getAmount(),
                item.getQuantity().getUnit().name(),
                item.getPurchasePrice().getAmount(),
                item.getExpirationInfo().getExpiresAt(),
                item.isNearExpiry(today, NEAR_EXPIRY_THRESHOLD_DAYS),
                item.getExpirationInfo().isShelfLifeExtended(),
                item.getParentFridgeItemId()
        );
    }
}
