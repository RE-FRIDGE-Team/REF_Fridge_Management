package com.refridge.fridge_management.fridge.controller.request;

import com.refridge.fridge_management.fridge.application.usecase.cook.CookIngredientCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 요리 재료 단건 Request DTO.
 *
 * <h2>UsageSpec 역직렬화 전략 (Option A)</h2>
 * {@code usageSpec}은 flat 구조의 {@link UsageSpecRequest}로 받은 뒤
 * {@link UsageSpecRequest#toDomain()}으로 도메인 객체로 변환한다.
 *
 * @param fridgeItemId 사용할 FridgeItem ID
 * @param usageSpec    사용량 명세
 *
 * @author 승훈
 * @since 2026-04-26
 */
public record CookIngredientRequest(
        @NotBlank(message = "fridgeItemId는 필수입니다")
        String fridgeItemId,

        @NotNull(message = "usageSpec은 필수입니다")
        @Valid
        UsageSpecRequest usageSpec
) {
    /**
     * Request DTO → Application Command 변환.
     */
    public CookIngredientCommand toCommand() {
        return new CookIngredientCommand(fridgeItemId, usageSpec.toDomain());
    }
}