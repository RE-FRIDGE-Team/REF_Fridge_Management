package com.refridge.fridge_management.fridge.domain.vo;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 금액 값 객체(Value Object).
 *
 * <h2>불변식</h2>
 * <ul>
 *   <li>amount는 항상 0 이상 (음수 불허).</li>
 *   <li>내부적으로 원(KRW) 단위, 소수점 없이 반올림 저장 (scale=0, HALF_UP).</li>
 * </ul>
 *
 * <h2>연산</h2>
 * 모든 산술 연산({@link #add}, {@link #subtract}, {@link #proportionalTo})은
 * 새 Money 인스턴스를 반환하며 기존 인스턴스를 변경하지 않는다.
 * {@code subtract}는 결과가 음수이면 즉시 예외를 던진다.
 * {@code proportionalTo}는 소분 시 구매가격을 비례 분할하는 데 사용된다.
 *
 * @author 승훈
 * @since 2026-04-20
 * @see FridgeMeta
 * @see Quantity#divideBy
 */
@Embeddable
// JPA @Embeddable은 protected no-args 생성자 필요.
// @Builder를 Money에 쓸 경우 외부에서 유효성 검증 없이 인스턴스 생성 가능 → of() 팩토리로 통제.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Money {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private BigDecimal amount;

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
