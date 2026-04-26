package com.refridge.fridge_management.fridge.infrastructure.grocery;

import com.refridge.fridge_management.fridge.domain.vo.GroceryItemRef.FoodCategory;

/**
 * core_server GroceryItem 단건 조회 응답 스냅샷.
 *
 * <h2>역할</h2>
 * {@link GroceryItemCatalogPort#fetch}가 반환하는 불변 DTO.
 * fridge_server가 core_server의 GroceryItem 도메인 모델에
 * 직접 의존하지 않도록 Anti-Corruption Layer 역할을 한다.
 *
 * <h2>소분 정책 필드</h2>
 * {@code minPortionAmount}, {@code maxPortionAmount}는
 * core_server portionPolicy에서 복사한 값이며,
 * {@code Fridge.portion()} 시 소분 단위 검증에 사용된다.
 *
 * @param groceryItemId     식재료 ID
 * @param name              식재료명
 * @param category          식품 카테고리
 * @param defaultUnit       기본 단위 (ex: "g", "개")
 * @param minPortionAmount  최소 소분 단위 (nullable)
 * @param maxPortionAmount  최대 소분 단위 (nullable)
 *
 * @author 승훈
 * @since 2026-04-26
 * @see GroceryItemCatalogPort
 */
public record GroceryItemSnapshot(
        String groceryItemId,
        String name,
        FoodCategory category,
        String defaultUnit,
        Integer minPortionAmount,
        Integer maxPortionAmount
) {}