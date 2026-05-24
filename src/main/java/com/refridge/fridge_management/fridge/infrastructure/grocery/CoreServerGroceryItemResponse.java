package com.refridge.fridge_management.fridge.infrastructure.grocery;

import java.util.List;

/**
 * core_server {@code GET /grocery-items/{id}/metadata} 응답 DTO.
 *
 * <h2>v4 변경점 (core_server v3 응답과 동기화)</h2>
 * <ul>
 *   <li>{@code allowedUsageTypes} → {@code allowedUsageUnits} (필드명 변경)</li>
 *   <li>{@code defaultUsageType} → {@code defaultUsageUnit} (필드명 변경)</li>
 *   <li>{@code spoonVolumeMl} 필드 <b>삭제</b> — core_server v3가 글로벌 상수(스푼=15ml)로 이전</li>
 *   <li>{@code minPortionAmount}, {@code maxPortionAmount}는 그대로 유지</li>
 * </ul>
 *
 * <h2>타입 주의</h2>
 * core_server의 {@code REFGroceryItem.id}와 {@code productId}는 {@code Long} 타입.
 * fridge_server 도메인에서는 {@code String}으로 저장하므로
 * {@link HttpGroceryItemAdapter}에서 {@code String.valueOf()}로 변환한다.
 *
 * <h2>응답 예시 (계란)</h2>
 * <pre>{@code
 * {
 *   "id": 50, "name": "계란", "category": "DAIRY",
 *   "defaultUnit": "EGG",
 *   "minPortionAmount": 1, "maxPortionAmount": 12,
 *   "allowedUsageUnits": ["EGG","GRAM"],
 *   "defaultUsageUnit": "EGG",
 *   "pieceWeightGram": 60,
 *   "productId": null
 * }
 * }</pre>
 *
 * <h2>응답 예시 (간장)</h2>
 * <pre>{@code
 * {
 *   "id": 1, "name": "간장", "category": "SAUCE_SEASONING",
 *   "defaultUnit": "ML",
 *   "allowedUsageUnits": ["TABLESPOON","TEASPOON","CUP","MILLILITER"],
 *   "defaultUsageUnit": "TABLESPOON",
 *   "productId": null
 * }
 * }</pre>
 *
 * <h2>category 필드</h2>
 * core_server의 {@code item_type} 값이 담긴다 (RAW_MEAT, GRAIN, DAIRY 등).
 * fridge_server FoodCategory와 다른 체계이며, {@link HttpGroceryItemAdapter#mapCategory}에서 변환한다.
 *
 * @author 승훈
 * @since 2026-05-15
 */
public record CoreServerGroceryItemResponse(
        Long id,
        String name,
        String category,

        /** 최종 차감 단위 (REFUsageUnit BASE only: GRAM/KILOGRAM/MILLILITER/LITER/PIECE/EGG/FISH/TOFU_BLOCK/CHEESE_SLICE/CAN) */
        String defaultUnit,

        Integer minPortionAmount,
        Integer maxPortionAmount,

        /**
         * 입력 가능한 단위 코드 리스트 (REFUsageUnit BASE/INPUT 모두 가능).
         * UI에서 단위 선택 버튼을 노출할 때 활용.
         * 예: 계란 → ["EGG","GRAM"], 간장 → ["TABLESPOON","TEASPOON","CUP","MILLILITER"]
         */
        List<String> allowedUsageUnits,

        /**
         * 첫 진입 시 활성화될 단위 코드 (allowedUsageUnits 중 하나).
         * 예: 계란 → "EGG", 간장 → "TABLESPOON", 쌀 → "CUP"
         */
        String defaultUsageUnit,

        /**
         * 1개(또는 1알·1마리·1모·1장·1캔)당 중량(g).
         * COUNT 계열 단위와 GRAM 환산에 사용.
         * 예: 계란 1알 = 60g, 두부 1모 = 300g
         * core_server에서 채워지지 않은 경우 null.
         */
        Integer pieceWeightGram,

        /** core_server의 productId (nullable, Long) */
        Long productId
) {}