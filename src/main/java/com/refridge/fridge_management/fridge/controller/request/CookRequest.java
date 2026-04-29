package com.refridge.fridge_management.fridge.controller.request;

import com.refridge.fridge_management.fridge.application.usecase.cook.CookCommand;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * 즉석 요리 Request DTO.
 * {@code POST /fridge/cook}
 *
 * @param ingredients         사용할 재료 목록 (1개 이상)
 * @param cookedGroceryItemId 요리 결과 GroceryItem ID (nullable — 없으면 임시 ref 구성)
 * @param cookedName          요리 이름 (사용자 입력)
 * @param servings            인분 수 (1 이상)
 * @param cookedExpiresAt     요리 결과 소비기한
 * @param targetSection       보관 구역
 *
 * @author 승훈
 * @since 2026-04-26
 */
public record CookRequest(
        @NotEmpty(message = "ingredients는 1개 이상이어야 합니다")
        @Valid
        List<CookIngredientRequest> ingredients,

        String cookedGroceryItemId,

        @NotBlank(message = "cookedName은 필수입니다")
        String cookedName,

        @Min(value = 1, message = "servings는 1 이상이어야 합니다")
        int servings,

        @NotNull(message = "cookedExpiresAt은 필수입니다")
        LocalDate cookedExpiresAt,

        @NotNull(message = "targetSection은 필수입니다")
        SectionType targetSection
) {
    /**
     * Request DTO → Application Command 변환.
     */
    public CookCommand toCommand() {
        return new CookCommand(
                ingredients.stream().map(CookIngredientRequest::toCommand).toList(),
                cookedGroceryItemId,
                cookedName,
                servings,
                cookedExpiresAt,
                targetSection
        );
    }
}