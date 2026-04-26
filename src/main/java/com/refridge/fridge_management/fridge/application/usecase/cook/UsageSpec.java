package com.refridge.fridge_management.fridge.application.usecase.cook;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 요리 재료 사용량 명세 (sealed interface).
 *
 * <h2>설계 의도</h2>
 * FridgeItem의 저장 단위(g, kg, EA 등)와 실제 요리에 사용하는 단위가 다를 수 있다.
 * 예:
 * <ul>
 *   <li>밀가루 1kg 중 3 큰술(tbsp) 사용 → SpoonUsage</li>
 *   <li>만두 1kg 중 10개 사용 → CountUsage</li>
 *   <li>두부 300g 전량 사용 → FullUsage</li>
 *   <li>돼지고기 500g 중 200g 사용 → QuantityUsage</li>
 *   <li>사용량을 정확히 모르지만 절반쯤 → RatioUsage</li>
 * </ul>
 *
 * <h2>핵심 메서드: toUsedRatio</h2>
 * 모든 구현체는 FridgeItem의 전체 수량 대비 사용 비율(0.0 ~ 1.0)을 반환한다.
 * CookUseCase가 이 비율로 {@code purchasePrice.proportionalTo(ratio)}를 계산해
 * 사용분 가격을 산출한다.
 * 1.0 초과는 1.0으로 클램핑 (전량 소비로 처리).
 *
 * <h2>v3 구현 범위</h2>
 * {@code FullUsage}, {@code QuantityUsage}, {@code RatioUsage} 구현.
 * {@code SpoonUsage}, {@code CountUsage}는 sealed permits에 선언하되 구현은 향후 추가.
 *
 * @author 승훈
 * @since 2026-04-26
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
     * @param totalAmount FridgeItem의 전체 수량 값 (amount, BigDecimal)
     * @param totalUnit   FridgeItem의 전체 수량 단위 (QuantityUnit.name())
     * @return 사용 비율 (1.0 초과 시 1.0으로 클램핑)
     */
    BigDecimal toUsedRatio(BigDecimal totalAmount, String totalUnit);

    // ── 전량 사용 ─────────────────────────────────────────────────────

    /**
     * 전량 사용 — 재료 FridgeItem 전체를 소비한다.
     * ratio = 1.0 고정.
     */
    record FullUsage() implements UsageSpec {
        @Override
        public BigDecimal toUsedRatio(BigDecimal totalAmount, String totalUnit) {
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
     * 단위 변환이 불가능한 경우(예: g → 개) {@link IllegalArgumentException} 발생.
     * 이 경우 클라이언트는 {@link RatioUsage}로 fallback해야 한다.
     *
     * @param usedAmount 사용 수량 값
     * @param usedUnit   사용 수량 단위 (QuantityUnit.name())
     */
    record QuantityUsage(BigDecimal usedAmount, String usedUnit) implements UsageSpec {
        @Override
        public BigDecimal toUsedRatio(BigDecimal totalAmount, String totalUnit) {
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

        private static boolean isSameUnitFamily(String a, String b) {
            String na = normalizeFamily(a);
            String nb = normalizeFamily(b);
            return na.equals(nb);
        }

        private static String normalizeFamily(String unit) {
            return switch (unit.toUpperCase()) {
                case "G", "KG"     -> "WEIGHT";
                case "ML", "L"     -> "VOLUME";
                default            -> unit.toUpperCase();
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
        public BigDecimal toUsedRatio(BigDecimal totalAmount, String totalUnit) {
            return ratio;
        }
    }

    // ── 스푼 기반 사용 (향후 구현) ────────────────────────────────────

    /**
     * 스푼(tsp/tbsp) 기반 사용량.
     * 가루·액상 재료(밀가루, 참기름 등)에서 스푼 단위로 계량할 때 사용한다.
     *
     * <h3>변환 근사값</h3>
     * <ul>
     *   <li>tsp(작은 술) ≈ 5ml</li>
     *   <li>tbsp(큰 술) ≈ 15ml</li>
     * </ul>
     * FridgeItem 단위가 g인 경우 ml→g 밀도 변환이 필요하며,
     * 재료마다 밀도가 달라 {@code GroceryItemRef}의 메타 정보 보강 후 구현 예정.
     *
     * @param spoons    스푼 수
     * @param spoonType 스푼 유형 (TSP=5ml, TBSP=15ml)
     */
    record SpoonUsage(BigDecimal spoons, SpoonType spoonType) implements UsageSpec {
        @Override
        public BigDecimal toUsedRatio(BigDecimal totalAmount, String totalUnit) {
            // TODO: GroceryItemRef 밀도 메타 보강 후 구현
            throw new UnsupportedOperationException(
                    "SpoonUsage는 아직 구현되지 않았습니다. RatioUsage를 사용하세요.");
        }

        public enum SpoonType {
            TSP(5),     // 작은 술 ≈ 5ml
            TBSP(15);   // 큰 술 ≈ 15ml

            public final int mlEquivalent;
            SpoonType(int ml) { this.mlEquivalent = ml; }
        }
    }

    // ── 개수 기반 사용 (향후 구현) ────────────────────────────────────

    /**
     * 개수 기반 사용량.
     * 예: 만두 1kg 중 만두 10개 사용.
     * 개당 중량({@code weightPerUnitGram})을 사용자가 직접 입력하거나
     * 향후 GroceryItemRef 메타에서 가져올 수 있다.
     *
     * @param count             사용 개수
     * @param weightPerUnitGram 개당 중량(g). null이면 균등 분할로 근사.
     */
    record CountUsage(int count, BigDecimal weightPerUnitGram) implements UsageSpec {
        @Override
        public BigDecimal toUsedRatio(BigDecimal totalAmount, String totalUnit) {
            // TODO: weightPerUnitGram이 없는 경우 GroceryItemRef 메타 활용 예정
            throw new UnsupportedOperationException(
                    "CountUsage는 아직 구현되지 않았습니다. RatioUsage를 사용하세요.");
        }
    }

    // ── 공통 헬퍼 ─────────────────────────────────────────────────────

    private static BigDecimal clamp(BigDecimal ratio) {
        return ratio.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : ratio;
    }
}