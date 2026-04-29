package com.refridge.fridge_management.fridge.controller.request;

import com.refridge.fridge_management.fridge.application.usecase.cook.UsageSpec;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 요리 재료 사용량 명세 Request DTO
 *
 * <h2>설계 의도</h2>
 * {@link UsageSpec}은 도메인 레이어의 sealed interface이므로
 * Jackson 기본 다형성 역직렬화가 불가능하다.
 * Request DTO 레벨에서 {@code type} 필드로 분기해 flat하게 받은 뒤,
 * {@link #toDomain()}으로 도메인 객체로 변환한다.
 * 이로써 도메인 레이어에 Jackson 의존성이 생기지 않는다.
 *
 * <h2>type별 필수 필드</h2>
 * <pre>
 * FULL    : (추가 필드 없음)
 * QUANTITY: amount, unit 필수
 * RATIO   : ratio 필수 (0.0 < ratio ≤ 1.0)
 * SPOON   : (향후 구현 — 현재 UnsupportedOperationException)
 * COUNT   : (향후 구현 — 현재 UnsupportedOperationException)
 * </pre>
 *
 * <h2>클라이언트 요청 예시</h2>
 * <pre>
 * // 전량 사용
 * { "type": "FULL" }
 *
 * // 200g 사용
 * { "type": "QUANTITY", "amount": 200, "unit": "G" }
 *
 * // 절반 사용
 * { "type": "RATIO", "ratio": 0.5 }
 * </pre>
 *
 * @author 승훈
 * @since 2026-04-26
 * @see UsageSpec
 */
public record UsageSpecRequest(
        @NotNull(message = "usageSpec.type은 필수입니다")
        UsageSpecType type,

        /** QUANTITY 타입 시 사용 수량 값 */
        BigDecimal amount,

        /** QUANTITY 타입 시 사용 수량 단위 (ex: "G", "ML", "EA") */
        String unit,

        /** RATIO 타입 시 사용 비율 (0.0 초과 ~ 1.0 이하) */
        @DecimalMin(value = "0.0", inclusive = false, message = "ratio는 0.0 초과여야 합니다")
        @DecimalMax(value = "1.0", message = "ratio는 1.0 이하여야 합니다")
        BigDecimal ratio
) {
    /**
     * Request DTO → 도메인 {@link UsageSpec} 변환.
     *
     * @throws IllegalArgumentException type에 따른 필수 필드 누락 시
     */
    public UsageSpec toDomain() {
        return switch (type) {
            case FULL -> new UsageSpec.FullUsage();

            case QUANTITY -> {
                if (amount == null || unit == null || unit.isBlank())
                    throw new IllegalArgumentException(
                            "QUANTITY 타입은 amount와 unit이 필수입니다.");
                yield new UsageSpec.QuantityUsage(amount, unit);
            }

            case RATIO -> {
                if (ratio == null)
                    throw new IllegalArgumentException(
                            "RATIO 타입은 ratio가 필수입니다.");
                yield new UsageSpec.RatioUsage(ratio);
            }

            case SPOON, COUNT ->
                    throw new IllegalArgumentException(
                            type.name() + " 타입은 아직 지원하지 않습니다. RATIO를 사용해주세요.");
        };
    }

    /** UsageSpec 타입 구분자 */
    public enum UsageSpecType {
        FULL,
        QUANTITY,
        RATIO,
        SPOON,
        COUNT
    }
}