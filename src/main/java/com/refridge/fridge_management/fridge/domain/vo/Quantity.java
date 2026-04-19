package com.refridge.fridge_management.fridge.domain.vo;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Embeddable
public class Quantity {

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private QuantityUnit unit;

    protected Quantity() {
    }

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

    public BigDecimal getAmount() {
        return amount;
    }

    public QuantityUnit getUnit() {
        return unit;
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