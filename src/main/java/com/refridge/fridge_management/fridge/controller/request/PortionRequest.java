package com.refridge.fridge_management.fridge.controller.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 소분 Request DTO.
 * {@code POST /fridge/items/{id}/portion}
 *
 * @param portionCount 소분 수 (2 이상)
 *
 * @author 승훈
 * @since 2026-04-26
 */
public record PortionRequest(
        @NotNull(message = "portionCount는 필수입니다")
        @Min(value = 2, message = "소분 수는 2 이상이어야 합니다")
        Integer portionCount
) {}