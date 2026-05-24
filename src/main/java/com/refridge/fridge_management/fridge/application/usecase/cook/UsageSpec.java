package com.refridge.fridge_management.fridge.application.usecase.cook;

import com.refridge.fridge_management.fridge.domain.FridgeItem;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 요리 재료 사용량 명세 (sealed interface).
 *
 * <h2>v4 변경점</h2>
 * <ul>
 *   <li>시그니처 변경: {@code toUsedRatio(BigDecimal, String)} → {@code toUsedRatio(FridgeItem)}.
 *       이유: {@link CountUsage}와 {@link SpoonUsage}는 {@code pieceWeightGram}과
 *       다른 메타데이터에 접근해야 하므로 FridgeItem 전체를 받는 것이 가장 유연.</li>
 *   <li>{@link CountUsage} 실제 구현 — core_server v3가 {@code pieceWeightGram}을
 *       제공하기 시작함에 따라 환산 가능.</li>
 *   <li>{@link SpoonUsage} 실제 구현 (액체 한정) — 글로벌 상수
 *       TSP=5ml, TBSP=15ml 사용.</li>
 * </ul>
 *
 * <h2>설계 의도</h2>
 * FridgeItem의 저장 단위(g, kg, EA 등)와 실제 요리에 사용하는 단위가 다를 수 있다.
 * 예:
 * <ul>
 *   <li>밀가루 1kg 중 3 큰술(tbsp) 사용 → SpoonUsage (액체만 직접 지원)</li>
 *   <li>만두 1kg 중 10개 사용 → CountUsage (pieceWeightGram=30 필요)</li>
 *   <li>두부 300g 전량 사용 → FullUsage</li>
 *   <li>돼지고기 500g 중 200g 사용 → QuantityUsage</li>
 *   <li>사용량을 정확히 모르지만 절반쯤 → RatioUsage</li>
 * </ul>
 *
 * <h2>핵심 메서드: toUsedRatio</h2>
 * 모든 구현체는 FridgeItem의 전체 수량 대비 사용 비율(0.0 ~ 1.0)을 반환한다.
 * CookUseCase가 이 비율로 {@code purchasePrice.proportionalTo(ratio)}를 계산해
 * 사용분 가격을 산출한다. 1.0 초과는 1.0으로 클램핑.
 *
 * @author 승훈
 * @since 2026-05-15
 */
public sealed interface UsageSpec permits
        UsageSpec.FullUsage,
        UsageSpec.QuantityUsage,
        UsageSpec.RatioUsage,
        UsageSpec.SpoonUsage,
        UsageSpec.CountUsage {

    /**
     * 핵심 메서드 — FridgeItem 전체 수량 대비 사용 비율 반환 (0.0 < ratio ≤ 1.0).
     *
     * @param item 사용 대상 FridgeItem (quantity, GroceryItemRef.pieceWeightGram 등 접근)
     * @return 사용 비율 (1.0 초과 시 1.0으로 클램핑)
     */
    BigDecimal toUsedRatio(FridgeItem item);

    // ── 전량 사용 ─────────────────────────────────────────────────────

    /**
     * 전량 사용 — 재료 FridgeItem 전체를 소비한다.
     * ratio = 1.0 고정.
     */
    record FullUsage() implements UsageSpec {
        @Override
        public BigDecimal toUsedRatio(FridgeItem item) {
            return BigDecimal.ONE;
        }
    }

    // ── 수량 기반 사용 ────────────────────────────────────────────────

    /**
     * 동일 계열 단위 수량 사용.
     * 예: 500g 아이템 중 200g 사용, 1L 아이템 중 300ml 사용.
     *
     * <h3>지원 변환</h3>
     * <ul>
     *   <li>g ↔ kg : ×1000 / ÷1000</li>
     *   <li>ml ↔ L : ×1000 / ÷1000</li>
     *   <li>동일 단위: 변환 없이 사용</li>
     * </ul>
     * 단위 변환이 불가능한 경우 {@link IllegalArgumentException} 발생 →
     * 클라이언트는 {@link RatioUsage}로 fallback해야 한다.
     *
     * @param usedAmount 사용 수량 값
     * @param usedUnit   사용 수량 단위 (QuantityUnit.name())
     */
    record QuantityUsage(BigDecimal usedAmount, String usedUnit) implements UsageSpec {
        @Override
        public BigDecimal toUsedRatio(FridgeItem item) {
            BigDecimal totalAmount = item.getQuantity().getAmount();
            String     totalUnit   = item.getQuantity().getUnit().name();

            BigDecimal normalizedUsed  = toBaseUnit(usedAmount, usedUnit);
            BigDecimal normalizedTotal = toBaseUnit(totalAmount, totalUnit);

            if (normalizedTotal.compareTo(BigDecimal.ZERO) == 0)
                throw new IllegalArgumentException("FridgeItem 총 수량이 0입니다.");

            BigDecimal ratio = normalizedUsed.divide(normalizedTotal, 4, RoundingMode.HALF_UP);
            return clamp(ratio);
        }

        /**
         * g/kg → g, ml/L → ml 기본 단위로 정규화.
         * EA, PACK, PIECE, SERVING은 그대로 사용 (변환 불필요).
         */
        private static BigDecimal toBaseUnit(BigDecimal amount, String unit) {
            return switch (unit.toUpperCase()) {
                case "KG"      -> amount.multiply(BigDecimal.valueOf(1000));
                case "L"       -> amount.multiply(BigDecimal.valueOf(1000));
                case "G", "ML",
                     "EA", "PACK",
                     "PIECE", "SERVING" -> amount;
                default -> throw new IllegalArgumentException(
                        "지원하지 않는 단위: " + unit + ". RatioUsage를 사용하세요.");
            };
        }
    }

    // ── 비율 직접 지정 ────────────────────────────────────────────────

    /**
     * 비율 직접 지정.
     * 사용자가 "절반 정도" 같이 정확한 수량을 모르거나,
     * 단위 변환이 불가한 경우(g → 개)의 fallback으로 사용한다.
     *
     * @param ratio 사용 비율 (0.0 초과 ~ 1.0 이하)
     */
    record RatioUsage(BigDecimal ratio) implements UsageSpec {
        public RatioUsage {
            if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0
                    || ratio.compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException(
                        "ratio는 0.0 초과 1.0 이하여야 합니다: " + ratio);
        }

        @Override
        public BigDecimal toUsedRatio(FridgeItem item) {
            return ratio;
        }
    }

    // ── 스푼 기반 사용 (액체 한정 — v4 구현) ─────────────────────────

    /**
     * 스푼(tsp/tbsp) 기반 사용량.
     * 액체 재료(간장·식초·참기름 등)에서 스푼 단위로 계량할 때 사용.
     *
     * <h3>v4 지원 범위</h3>
     * 액체(ML, L) 단위 FridgeItem에 한정 — 글로벌 상수 변환:
     * <ul>
     *   <li>TSP(작은 술) = 5ml</li>
     *   <li>TBSP(큰 술) = 15ml</li>
     * </ul>
     *
     * <h3>v4에서 지원하지 않는 케이스</h3>
     * 가루(밀가루·설탕 등) FridgeItem이 g/kg로 저장된 경우 밀도 환산이 필요.
     * 밀도 메타데이터가 없으므로 {@link IllegalArgumentException} 발생 →
     * 클라이언트는 {@link RatioUsage}로 fallback.
     *
     * @param spoons    스푼 수 (양의 정수, 소수도 허용)
     * @param spoonType 스푼 유형 (TSP=5ml, TBSP=15ml)
     */
    record SpoonUsage(BigDecimal spoons, SpoonType spoonType) implements UsageSpec {

        public SpoonUsage {
            if (spoons == null || spoons.compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException("spoons는 양수여야 합니다: " + spoons);
            if (spoonType == null)
                throw new IllegalArgumentException("spoonType은 필수입니다.");
        }

        @Override
        public BigDecimal toUsedRatio(FridgeItem item) {
            String totalUnit = item.getQuantity().getUnit().name();

            // 액체 단위만 직접 지원 (밀도 메타 없이 ml ↔ g 환산 불가)
            if (!isLiquidUnit(totalUnit)) {
                throw new IllegalArgumentException(
                        "SpoonUsage는 액체 단위(ML/L)만 지원합니다. " +
                                "현재 단위: " + totalUnit + ". RatioUsage를 사용하세요.");
            }

            // 사용량(ml) = 스푼 수 × 스푼당 ml
            BigDecimal usedMl = spoons.multiply(BigDecimal.valueOf(spoonType.mlEquivalent));

            // 총량(ml) — L이면 ×1000
            BigDecimal totalMl = "L".equals(totalUnit)
                    ? item.getQuantity().getAmount().multiply(BigDecimal.valueOf(1000))
                    : item.getQuantity().getAmount();

            if (totalMl.compareTo(BigDecimal.ZERO) == 0)
                throw new IllegalArgumentException("FridgeItem 총 수량이 0입니다.");

            BigDecimal ratio = usedMl.divide(totalMl, 4, RoundingMode.HALF_UP);
            return clamp(ratio);
        }

        private static boolean isLiquidUnit(String unit) {
            return "ML".equals(unit) || "L".equals(unit);
        }

        public enum SpoonType {
            TSP(5),     // 작은 술 = 5ml
            TBSP(15);   // 큰 술 = 15ml

            public final int mlEquivalent;
            SpoonType(int ml) { this.mlEquivalent = ml; }
        }
    }

    // ── 개수 기반 사용 (v4 구현) ──────────────────────────────────────

    /**
     * 개수 기반 사용량.
     * 예: 계란 12알 중 3알 사용, 만두 1kg(30개) 중 10개 사용.
     *
     * <h3>v4 구현 — pieceWeightGram 기반 환산</h3>
     * core_server v3가 {@code GroceryItemRef.pieceWeightGram}을 제공하므로
     * 클라이언트는 개수만 보내면 fridge_server가 자동 환산.
     *
     * <h3>지원 케이스</h3>
     * <ul>
     *   <li>FridgeItem 단위가 EA/PIECE — 개수 비율 직접 계산: {@code count / totalAmount}</li>
     *   <li>FridgeItem 단위가 G/KG + pieceWeightGram 보유 —
     *       사용 g = {@code count × pieceWeightGram}, 총 g과 비교</li>
     * </ul>
     *
     * <h3>지원하지 않는 케이스</h3>
     * pieceWeightGram이 null이고 FridgeItem 단위가 EA 계열이 아닌 경우 →
     * {@link IllegalStateException} 발생, RatioUsage로 fallback 권장.
     *
     * @param count 사용 개수 (1 이상)
     */
    record CountUsage(int count) implements UsageSpec {

        public CountUsage {
            if (count < 1)
                throw new IllegalArgumentException("count는 1 이상이어야 합니다: " + count);
        }

        @Override
        public BigDecimal toUsedRatio(FridgeItem item) {
            String totalUnit = item.getQuantity().getUnit().name();
            BigDecimal totalAmount = item.getQuantity().getAmount();

            // 케이스 1: FridgeItem이 개수 단위로 저장된 경우 (EA/PIECE)
            if (isCountUnit(totalUnit)) {
                if (totalAmount.compareTo(BigDecimal.ZERO) == 0)
                    throw new IllegalArgumentException("FridgeItem 총 수량이 0입니다.");
                BigDecimal ratio = BigDecimal.valueOf(count)
                        .divide(totalAmount, 4, RoundingMode.HALF_UP);
                return clamp(ratio);
            }

            // 케이스 2: FridgeItem이 무게 단위(G/KG) + pieceWeightGram 보유
            Integer pieceWeightGram = item.getGroceryItemRef().getPieceWeightGram();
            if (pieceWeightGram != null && pieceWeightGram > 0
                    && isWeightUnit(totalUnit)) {
                BigDecimal usedG  = BigDecimal.valueOf((long) count * pieceWeightGram);
                BigDecimal totalG = "KG".equals(totalUnit)
                        ? totalAmount.multiply(BigDecimal.valueOf(1000))
                        : totalAmount;

                if (totalG.compareTo(BigDecimal.ZERO) == 0)
                    throw new IllegalArgumentException("FridgeItem 총 수량이 0입니다.");

                BigDecimal ratio = usedG.divide(totalG, 4, RoundingMode.HALF_UP);
                return clamp(ratio);
            }

            // 그 외: 환산 불가
            throw new IllegalStateException(
                    "CountUsage 환산 불가 — FridgeItem 단위(%s)가 개수 계열이 아니거나 " +
                            "pieceWeightGram이 없습니다. RatioUsage를 사용하세요. " +
                            "groceryItemId=%s, pieceWeightGram=%s"
                                    .formatted(totalUnit,
                                            item.getGroceryItemRef().getGroceryItemId(),
                                            pieceWeightGram));
        }

        private static boolean isCountUnit(String unit) {
            return "EA".equals(unit) || "PIECE".equals(unit) || "PACK".equals(unit);
        }

        private static boolean isWeightUnit(String unit) {
            return "G".equals(unit) || "KG".equals(unit);
        }
    }

    // ── 공통 헬퍼 ─────────────────────────────────────────────────────

    private static BigDecimal clamp(BigDecimal ratio) {
        return ratio.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : ratio;
    }
}