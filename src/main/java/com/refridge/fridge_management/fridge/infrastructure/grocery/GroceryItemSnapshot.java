package com.refridge.fridge_management.fridge.infrastructure.grocery;

import com.refridge.fridge_management.fridge.domain.vo.GroceryItemRef.FoodCategory;

import java.util.List;

/**
 * core_server GroceryItem 단건 조회 응답 스냅샷.
 *
 * <h2>v4 변경점 (단위 메타데이터 3종 추가)</h2>
 * <ul>
 *   <li>{@code allowedUsageUnits} — 입력 가능한 단위 코드 리스트</li>
 *   <li>{@code defaultUsageUnit} — 기본 활성 단위 코드</li>
 *   <li>{@code pieceWeightGram} — 1개당 중량(g)</li>
 * </ul>
 * 이 정보는 {@code FillFridgeUseCase}에서 {@code GroceryItemRef}에 영구 저장되어,
 * 이후 Cook 등에서 core_server를 다시 호출하지 않고 활용된다.
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
 * @param groceryItemId      식재료 ID
 * @param name               식재료명
 * @param category           식품 카테고리
 * @param defaultUnit        기본 차감 단위 (BASE 단위 코드)
 * @param minPortionAmount   최소 소분 단위 (nullable)
 * @param maxPortionAmount   최대 소분 단위 (nullable)
 * @param allowedUsageUnits  입력 가능 단위 코드 리스트 (nullable)
 * @param defaultUsageUnit   기본 활성 단위 코드 (nullable)
 * @param pieceWeightGram    1개당 중량 g (nullable, COUNT 계열일 때 유의미)
 *
 * @author 승훈
 * @since 2026-05-15
 * @see GroceryItemCatalogPort
 */
public record GroceryItemSnapshot(
        String groceryItemId,
        String name,
        FoodCategory category,
        String defaultUnit,
        Integer minPortionAmount,
        Integer maxPortionAmount,
        List<String> allowedUsageUnits,
        String defaultUsageUnit,
        Integer pieceWeightGram
) {}