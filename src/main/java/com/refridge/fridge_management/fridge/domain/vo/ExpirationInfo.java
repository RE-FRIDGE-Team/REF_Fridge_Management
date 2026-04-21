package com.refridge.fridge_management.fridge.domain.vo;

import jakarta.persistence.Embeddable;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 유통기한 정보 값 객체(Value Object).
 *
 * <h2>연장 규칙</h2>
 * <ul>
 *   <li>연장은 1회만 가능하다 ({@code shelfLifeExtended} 플래그 확인).</li>
 *   <li>연장 후 originalExpiresAt에 원래 만료일이 보존된다.</li>
 *   <li>{@link #extend(int)}는 새 불변 인스턴스를 반환한다.</li>
 * </ul>
 *
 * <h2>임박 판단 기준</h2>
 * {@link #isNearExpiry}: {@code 0 ≤ daysUntilExpiry ≤ thresholdDays} (당일 포함, 만료일 당일 = 0일)
 * {@link #isExpired}: {@code daysUntilExpiry < 0}
 *
 * @author 승훈
 * @since 2025-04-21
 * @see com.refridge.fridge_management.fridge.domain.Fridge#extend(String, int, LocalDate) 
 * @see com.refridge.fridge_management.fridge.domain.Fridge#registerNearExpiryEvents(LocalDate, int) 
 */
@Embeddable
public class ExpirationInfo {

    private LocalDate manufacturedAt;
    private LocalDate expiresAt;
    private boolean shelfLifeExtended;
    private LocalDate originalExpiresAt;

    protected ExpirationInfo() {
    }

    private ExpirationInfo(LocalDate manufacturedAt, LocalDate expiresAt,
                           boolean extended, LocalDate originalExpiresAt) {
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.manufacturedAt = manufacturedAt;
        this.shelfLifeExtended = extended;
        this.originalExpiresAt = originalExpiresAt;
    }

    public static ExpirationInfo of(LocalDate expiresAt) {
        return new ExpirationInfo(null, expiresAt, false, null);
    }

    public static ExpirationInfo of(LocalDate manufacturedAt, LocalDate expiresAt) {
        return new ExpirationInfo(manufacturedAt, expiresAt, false, null);
    }

    /**
     * 연장 후 새 불변 인스턴스 반환
     */
    public ExpirationInfo extend(int additionalDays) {
        if (shelfLifeExtended)
            throw new IllegalStateException("이미 기한이 연장된 아이템입니다.");
        if (additionalDays <= 0)
            throw new IllegalArgumentException("연장 일수는 양의 정수여야 합니다: " + additionalDays);
        return new ExpirationInfo(manufacturedAt, expiresAt.plusDays(additionalDays), true, expiresAt);
    }

    public long daysUntilExpiry(LocalDate today) {
        return ChronoUnit.DAYS.between(today, expiresAt);
    }

    public boolean isNearExpiry(LocalDate today, int thresholdDays) {
        long days = daysUntilExpiry(today);
        return days >= 0 && days <= thresholdDays;
    }

    public boolean isExpired(LocalDate today) {
        return daysUntilExpiry(today) < 0;
    }

    public LocalDate getManufacturedAt() {
        return manufacturedAt;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
    }

    public boolean isShelfLifeExtended() {
        return shelfLifeExtended;
    }

    public LocalDate getOriginalExpiresAt() {
        return originalExpiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpirationInfo e)) return false;
        return shelfLifeExtended == e.shelfLifeExtended
                && Objects.equals(manufacturedAt, e.manufacturedAt)
                && Objects.equals(expiresAt, e.expiresAt)
                && Objects.equals(originalExpiresAt, e.originalExpiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(manufacturedAt, expiresAt, shelfLifeExtended, originalExpiresAt);
    }

    @Override
    public String toString() {
        return "ExpirationInfo{expiresAt=%s, extended=%s}".formatted(expiresAt, shelfLifeExtended);
    }
}