package com.refridge.fridge_management.fridge.domain.vo;

import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * 냉장고 집계 메타 VO.
 * 불변식: activeItemCount ≥ 0, totalValue ≥ 0
 * 두 값은 항상 ACTIVE FridgeItem 목록과 일치해야 한다.
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