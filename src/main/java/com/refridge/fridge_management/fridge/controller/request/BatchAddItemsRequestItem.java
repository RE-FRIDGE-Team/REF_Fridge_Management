package com.refridge.fridge_management.fridge.controller.request;

import com.refridge.fridge_management.fridge.application.usecase.fill.FillFridgeCommand;
import com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent.ItemProcessingType;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;
import com.refridge.fridge_management.fridge.domain.vo.UserEditedField;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * 냉장고 채우기 단건 Request DTO.
 *
 * <h2>v4 변경점</h2>
 * <ul>
 *   <li>{@code expiresAt} 제거 — 채우기 시점에 소비기한을 입력받지 않음</li>
 *   <li>{@code manufacturedAt} 제거</li>
 *   <li>소비기한은 이후 {@code POST /fridge/items/{id}/expiration}으로 별도 등록</li>
 * </ul>
 *
 * @param recognitionId    Recognition AR ID (직접 입력 시 null)
 * @param groceryItemId    식재료 ID
 * @param productId        제품 ID (nullable)
 * @param finalBrandName   최종 브랜드명 (nullable)
 * @param quantityAmount   수량 값
 * @param quantityUnit     수량 단위 (ex: "G", "EA", "ML")
 * @param purchasePrice    구매 가격 (원, 양수)
 * @param sectionType      보관 구역
 * @param processingType   가공 유형
 * @param userEditedFields 사용자가 수정한 필드 집합 (null이면 빈 Set으로 처리)
 *
 * @author 승훈
 * @since 2026-04-26
 * @see FillFridgeCommand
 */
public record BatchAddItemsRequestItem(
        UUID recognitionId,

        @NotBlank(message = "groceryItemId는 필수입니다")
        String groceryItemId,

        String productId,
        String finalBrandName,

        @NotNull(message = "quantityAmount는 필수입니다")
        @Positive(message = "quantityAmount는 양수여야 합니다")
        BigDecimal quantityAmount,

        @NotBlank(message = "quantityUnit은 필수입니다")
        String quantityUnit,

        @Positive(message = "purchasePrice는 양수여야 합니다")
        long purchasePrice,

        @NotNull(message = "sectionType은 필수입니다")
        SectionType sectionType,

        @NotNull(message = "processingType은 필수입니다")
        ItemProcessingType processingType,

        Set<UserEditedField> userEditedFields
) {
    /**
     * Request DTO → Application Command 변환.
     */
    public FillFridgeCommand toCommand() {
        return new FillFridgeCommand(
                recognitionId,
                groceryItemId,
                productId,
                finalBrandName,
                quantityAmount,
                quantityUnit,
                purchasePrice,
                sectionType,
                processingType,
                userEditedFields   // null이면 FillFridgeCommand 생성자에서 빈 Set으로 처리
        );
    }
}