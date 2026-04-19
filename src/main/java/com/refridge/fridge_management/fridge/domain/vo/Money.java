package com.refridge.fridge_management.fridge.domain.vo;

import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Embeddable
public class Money {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private BigDecimal amount;

    protected Money() {
        this.amount = BigDecimal.ZERO;
    }

    private Money(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("amount must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        this.amount = amount.setScale(0, RoundingMode.HALF_UP);
    }

    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        if (this.amount.compareTo(other.amount) < 0)
            throw new IllegalArgumentException(
                    "결과가 음수: this=%s, other=%s".formatted(this.amount, other.amount));
        return new Money(this.amount.subtract(other.amount));
    }

    /**
     * 비율 분할: price × (numerator / denominator) — 소분 가격 계산용
     */
    public Money proportionalTo(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(BigDecimal.ZERO) == 0)
            throw new IllegalArgumentException("denominator must not be zero");
        return new Money(amount.multiply(numerator).divide(denominator, 0, RoundingMode.HALF_UP));
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money m)) return false;
        return amount.compareTo(m.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return amount.toPlainString() + "원";
    }
}