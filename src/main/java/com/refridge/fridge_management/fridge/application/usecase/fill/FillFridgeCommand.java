package com.refridge.fridge_management.fridge.application.usecase.fill;

import com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent.ItemProcessingType;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;
import com.refridge.fridge_management.fridge.domain.vo.UserEditedField;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * 냉장고 채우기 단건 커맨드.
 *
 * <h2>소비기한 정책</h2>
 * 채우기(인식) 시점에는 소비기한을 입력받지 않는다.
 * FridgeItem은 {@code ExpirationInfo.unset()} 상태로 생성되며,
 * 이후 {@code POST /fridge/items/{id}/expiration}으로 별도 등록한다.
 *
 * <h2>제거된 필드</h2>
 * <ul>
 *   <li>{@code manufacturedAt} — 제조일은 채우기 흐름에서 불필요</li>
 *   <li>{@code expiresAt} — 채우기 시점에 입력받지 않음 (별도 등록)</li>
 * </ul>
 *
 * @param recognitionId    Recognition AR ID (직접 입력 시 null)
 * @param groceryItemId    식재료 ID
 * @param productId        제품 ID (nullable)
 * @param finalBrandName   최종 브랜드명 (nullable)
 * @param quantityAmount   수량 값
 * @param quantityUnit     수량 단위 (ex: "G", "EA")
 * @param purchasePrice    구매 가격 (원)
 * @param sectionType      보관 구역
 * @param processingType   가공 유형
 * @param userEditedFields 사용자가 수정한 필드 집합
 *
 * @author 승훈
 * @since 2026-04-26
 */
public record FillFridgeCommand(
        UUID recognitionId,
        String groceryItemId,
        String productId,
        String finalBrandName,
        BigDecimal quantityAmount,
        String quantityUnit,
        long purchasePrice,
        SectionType sectionType,
        ItemProcessingType processingType,
        Set<UserEditedField> userEditedFields
) {
    public FillFridgeCommand {
        if (userEditedFields == null) userEditedFields = Set.of();
    }
}