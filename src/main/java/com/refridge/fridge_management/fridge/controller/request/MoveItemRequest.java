package com.refridge.fridge_management.fridge.controller.request;

import com.refridge.fridge_management.fridge.domain.vo.SectionType;
import jakarta.validation.constraints.NotNull;

/**
 * 구역 이동 Request DTO.
 * {@code PATCH /fridge/items/{id}/section}
 *
 * <h2>상행성 이동 처리</h2>
 * 냉동→냉장, 냉동→상온, 냉장→상온 방향(온도 상승) 이동 시
 * 응답의 {@code wasUpwardMove=true}를 클라이언트가 확인해 경고 UI를 표시한다.
 * 서버는 경고를 발행하되 이동 자체를 막지는 않는다
 * (사용자가 UI에서 확인 후 호출하는 흐름이므로).
 *
 * @param targetSection 이동할 목적지 구역
 *
 * @author 승훈
 * @since 2026-04-26
 */
public record MoveItemRequest(
        @NotNull(message = "targetSection은 필수입니다")
        SectionType targetSection
) {}