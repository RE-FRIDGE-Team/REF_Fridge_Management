package com.refridge.fridge_management.fridge.domain.vo;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 수량 값 객체(Value Object).
 *
 * <h2>단위 체계</h2>
 * {@link QuantityUnit}: G(그램), KG, ML, L, EA(개), PACK(팩), PIECE(조각), SERVING(인분)
 * 소분({@link #divideBy}) 시 같은 단위가 유지된다.
 *
 * <h2>소분 연산</h2>
 * {@code divideBy(n)}은 amount를 n등분한 새 Quantity를 반환한다.
 * 결과 수량이 0 이하이면 예외를 던진다 (예: 0.5g 아이템을 2등분 시도 → 0.25g → 허용).
 *
 * @author 승훈
 * @since 2026-04-21
 * @see com.refridge.fridge_management.fridge.domain.Fridge#portion(String, int)
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quantity {

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private QuantityUnit unit;

    private Quantity(BigDecimal amount, QuantityUnit unit) {
        if (amount == null || unit == null)
            throw new IllegalArgumentException("amount and unit must not be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("amount must be positive: " + amount);
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.unit = unit;
    }

    public static Quantity of(BigDecimal amount, QuantityUnit unit) {
        return new Quantity(amount, unit);
    }

    public static Quantity of(long amount, QuantityUnit unit) {
        return new Quantity(BigDecimal.valueOf(amount), unit);
    }

    /**
     * N등분 중 1개 수량 반환
     */
    public Quantity divideBy(int portions) {
        if (portions <= 0) throw new IllegalArgumentException("portions must be positive: " + portions);
        BigDecimal portionAmount = amount.divide(BigDecimal.valueOf(portions), 2, RoundingMode.HALF_UP);
        if (portionAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("소분 결과 수량이 0 이하: amount=%s, portions=%d".formatted(amount, portions));
        return new Quantity(portionAmount, unit);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Quantity q)) return false;
        return amount.compareTo(q.amount) == 0 && unit == q.unit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), unit);
    }

    @Override
    public String toString() {
        return amount.stripTrailingZeros().toPlainString() + " " + unit.symbol;
    }

    public enum QuantityUnit {
        G("g"), KG("kg"), ML("ml"), L("L"),
        EA("개"), PACK("팩"), PIECE("조각"), SERVING("인분");

        private final String symbol;

        QuantityUnit(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }
    }
}
