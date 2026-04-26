package com.refridge.fridge_management.fridge.application.usecase.cook;

/**
 * 즉석 요리 재료 단건 커맨드.
 *
 * <h2>usageSpec 설계</h2>
 * FridgeItem의 저장 단위와 실제 사용 단위가 다를 수 있어
 * {@link UsageSpec} sealed interface로 사용량을 추상화한다.
 *
 * <h3>클라이언트 전송 예시</h3>
 * <pre>
 * // 두부 300g 전량 사용
 * { "fridgeItemId": "...", "usageSpec": { "type": "FULL" } }
 *
 * // 돼지고기 500g 중 200g 사용
 * { "fridgeItemId": "...", "usageSpec": { "type": "QUANTITY", "amount": 200, "unit": "G" } }
 *
 * // 만두 1kg 중 절반만 사용 (개수나 중량을 정확히 모를 때)
 * { "fridgeItemId": "...", "usageSpec": { "type": "RATIO", "ratio": 0.5 } }
 * </pre>
 *
 * @param fridgeItemId 사용할 FridgeItem ID (ACTIVE 상태여야 함)
 * @param usageSpec    사용량 명세 (전량/수량/비율/스푼/개수)
 *
 * @author 승훈
 * @since 2026-04-26
 * @see UsageSpec
 */
public record CookIngredientCommand(
        String fridgeItemId,
        UsageSpec usageSpec
) {
    public CookIngredientCommand {
        if (fridgeItemId == null || fridgeItemId.isBlank())
            throw new IllegalArgumentException("fridgeItemId must not be blank");
        if (usageSpec == null)
            throw new IllegalArgumentException("usageSpec must not be null");
    }

    /** 전량 사용 편의 팩토리 */
    public static CookIngredientCommand fullUsage(String fridgeItemId) {
        return new CookIngredientCommand(fridgeItemId, new UsageSpec.FullUsage());
    }
}