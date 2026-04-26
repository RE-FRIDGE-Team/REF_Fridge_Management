package com.refridge.fridge_management.fridge.application.usecase.extend;

import com.refridge.fridge_management.fridge.infrastructure.advisor.ShelfLifeRecommendation;

import java.time.LocalDate;

/**
 * 소비기한 연장 결과 DTO.
 *
 * @param fridgeItemId       연장된 아이템 ID
 * @param originalExpiresAt  연장 전 소비기한
 * @param newExpiresAt       연장 후 소비기한
 * @param additionalDays     이번 연장 일수
 * @param extensionCount     연장 후 누적 연장 횟수
 * @param recommendation     어드바이저 추천 정보 (사용자 직접 지정 시 null)
 *
 * @author 승훈
 * @since 2026-04-26
 */
public record ExtendShelfLifeResult(
        String fridgeItemId,
        LocalDate originalExpiresAt,
        LocalDate newExpiresAt,
        int additionalDays,
        int extensionCount,
        ShelfLifeRecommendation recommendation
) {}