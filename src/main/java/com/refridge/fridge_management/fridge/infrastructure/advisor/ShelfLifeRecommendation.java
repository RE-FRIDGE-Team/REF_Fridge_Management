package com.refridge.fridge_management.fridge.infrastructure.advisor;

/**
 * 소비기한 연장 추천 결과.
 *
 * @param recommendedDays 추천 연장 일수
 * @param confidence      추천 신뢰도 (0.0 ~ 1.0)
 * @param reasoning       추천 근거 설명 (UI 표시 및 사용자 판단 보조용)
 *
 * @author 승훈
 * @since 2026-04-26
 * @see ShelfLifeAdvisorPort
 */
public record ShelfLifeRecommendation(
        int recommendedDays,
        double confidence,
        String reasoning
) {}