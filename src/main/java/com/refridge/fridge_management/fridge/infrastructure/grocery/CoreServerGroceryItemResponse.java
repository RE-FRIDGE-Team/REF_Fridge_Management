package com.refridge.fridge_management.fridge.infrastructure.grocery;

import java.util.List;

/**
 * core_server {@code GET /grocery-items/{id}} 응답 DTO.
 *
 * <h2>타입 주의</h2>
 * core_server의 {@code REFGroceryItem.id}와 {@code productId}는 {@code Long} 타입이다.
 * fridge_server 도메인에서는 {@code String}으로 저장하므로
 * {@link HttpGroceryItemAdapter}에서 {@code String.valueOf()}로 변환한다.
 *
 * <h2>category 필드</h2>
 * core_server의 {@code item_type} 값이 담긴다.
 * (RAW_MEAT, GRAIN, DAIRY 등 — fridge_server FoodCategory와 다른 체계)
 * {@link HttpGroceryItemAdapter#mapCategory}에서 {@code FoodCategory}로 변환한다.
 *
 * <h2>단위 메타데이터 (신규 — core_server 개발 후 활성화)</h2>
 * {@code allowedUsageTypes}, {@code pieceWeightGram}, {@code spoonVolumeMl},
 * {@code defaultUsageType}은 core_server API 미완성으로 현재 null로 수신된다.
 * {@link HttpGroceryItemAdapter}에서 null-safe하게 처리한다.
 *
 * @author 승훈
 * @since 2026-04-26
 */
public record CoreServerGroceryItemResponse(
        Long id,
        String name,
        String category,       // core_server item_type (RAW_MEAT, GRAIN 등)
        String defaultUnit,
        Integer minPortionAmount,
        Integer maxPortionAmount,

        // ── 요리 사용량 단위 메타데이터 (core_server 개발 후 활성화) ────
        // 허용된 UsageSpec 유형 목록 (FULL, COUNT, QUANTITY, RATIO, SPOON)
        List<String> allowedUsageTypes,

        // 1개(EA)당 중량(g) — CountUsage ↔ QuantityUsage 변환용
        // 예: 만두 1개 = 30g, 달걀 1개 = 60g
        Integer pieceWeightGram,

        // 1스푼(TABLESPOON) 기준 부피(ml) — SpoonUsage ↔ QuantityUsage 변환용
        // 티스푼(TEASPOON) = TABLESPOON / 3
        Integer spoonVolumeMl,

        // 요리 UI에서 기본으로 선택할 UsageSpec 유형
        // 예: 만두 → COUNT, 식용유 → SPOON, 채소 → RATIO
        String defaultUsageType,

        // core_server의 productId (nullable, Long)
        Long productId
) {}