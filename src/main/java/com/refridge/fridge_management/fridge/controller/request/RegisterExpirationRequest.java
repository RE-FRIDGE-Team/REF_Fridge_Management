package com.refridge.fridge_management.fridge.controller.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 소비기한 등록 Request DTO.
 * {@code POST /fridge/items/{id}/expiration}
 *
 * <h2>정책</h2>
 * <ul>
 *   <li>채우기 이후 사용자가 실물 확인 후 별도 등록</li>
 *   <li>이미 소비기한이 등록된 아이템도 수정 허용 (입력 오류 정정)</li>
 *   <li>과거 날짜도 허용 — 이미 만료된 아이템에 소비기한을 소급 등록하는 케이스 고려</li>
 * </ul>
 *
 * @param expiresAt 등록할 소비기한
 *
 * @author 승훈
 * @since 2026-04-26
 */
public record RegisterExpirationRequest(
        @NotNull(message = "expiresAt은 필수입니다")
        LocalDate expiresAt
) {}