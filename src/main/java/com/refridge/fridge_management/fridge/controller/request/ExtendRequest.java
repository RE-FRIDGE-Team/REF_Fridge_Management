package com.refridge.fridge_management.fridge.controller.request;

import jakarta.validation.constraints.Positive;

/**
 * 소비기한 연장 Request DTO.
 * {@code POST /fridge/items/{id}/extend}
 *
 * <h2>additionalDays 처리 정책</h2>
 * <ul>
 *   <li>{@code null} — LLM 어드바이저({@code ShelfLifeAdvisorPort})가 추천 일수 결정</li>
 *   <li>양의 정수 — 사용자가 직접 지정한 일수 사용</li>
 * </ul>
 * UI 흐름: LLM 추천값을 먼저 표시하고, 사용자가 수정 후 확정하면 해당 값을 전송.
 * 추천값을 그대로 사용할 경우 {@code null}로 전송.
 *
 * @param additionalDays 연장 일수 (null이면 LLM 추천 경로)
 *
 * @author 승훈
 * @since 2026-04-26
 */
public record ExtendRequest(
        @Positive(message = "additionalDays는 양수여야 합니다")
        Integer additionalDays   // nullable — null이면 LLM 추천
) {}