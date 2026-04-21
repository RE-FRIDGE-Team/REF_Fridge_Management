package com.refridge.fridge_management.fridge.domain.vo;

import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * 냉장고 집계 메타 값 객체(Value Object).
 *
 * <h2>역할과 불변식</h2>
 * Fridge 수준의 두 집계 값을 캡슐화한다:
 * <ul>
 *   <li>{@code totalValue} = 현재 ACTIVE FridgeItem 구매가 총합</li>
 *   <li>{@code activeItemCount} = 현재 ACTIVE FridgeItem 수 (항상 ≥ 0)</li>
 * </ul>
 * 아이템이 추가/제거될 때마다 {@link #addItem}/{@link #removeItem}으로
 * 새 인스턴스를 생성해 교체한다 (불변 교체 패턴).
 *
 * <h2>주의</h2>
 * 이 값은 JPA Dirty Checking으로 자동 동기화된다.
 * 도메인 로직 외부에서 직접 조작하면 불변식이 깨질 수 있다.
 *
 * @author 승훈
 * @since 2025-06-01
 * @see com.refridge.fridge_management.fridge.domain.Fridge#fridgeMeta
 */
@Embeddable
public class FridgeMeta {

    private Money totalValue;
    private int activeItemCount;

    protected FridgeMeta() {
        this.totalValue      = Money.ZERO;
        this.activeItemCount = 0;
    }

    private FridgeMeta(Money totalValue, int activeItemCount) {
        if (activeItemCount < 0)
            throw new IllegalArgumentException("activeItemCount must not be negative: " + activeItemCount);
        this.totalValue      = Objects.requireNonNull(totalValue);
        this.activeItemCount = activeItemCount;
    }

    public static FridgeMeta empty() { return new FridgeMeta(Money.ZERO, 0); }

    public FridgeMeta addItem(Money price) {
        return new FridgeMeta(totalValue.add(price), activeItemCount + 1);
    }

    public FridgeMeta removeItem(Money price) {
        return new FridgeMeta(totalValue.subtract(price), activeItemCount - 1);
    }

    public Money getTotalValue()    { return totalValue; }
    public int getActiveItemCount() { return activeItemCount; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FridgeMeta m)) return false;
        return activeItemCount == m.activeItemCount && totalValue.equals(m.totalValue);
    }
    @Override public int hashCode() { return Objects.hash(totalValue, activeItemCount); }
    @Override public String toString() {
        return "FridgeMeta{totalValue=%s, activeItemCount=%d}".formatted(totalValue, activeItemCount);
    }
}