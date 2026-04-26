package com.refridge.fridge_management.fridge.domain.vo;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 소비기한 정보 값 객체(Value Object).
 *
 * <h2>소비기한 vs 유통기한</h2>
 * 법적 유통기한(제조사 표기)과 달리, 소비기한은 실제 섭취 가능 여부를 기준으로 한다.
 * 사용자가 냉동 미개봉 상태로 보관했다면 유통기한 이후에도 소비 가능할 수 있으므로,
 * 이 시스템에서는 사용자가 직접 소비기한을 관리·연장할 수 있도록 설계한다.
 *
 * <h2>연장 정책</h2>
 * <ul>
 *   <li>연장 횟수 제한 없음 — 사용자가 판단해 반복 연장 가능.</li>
 *   <li>{@code originalExpiresAt}: 최초 소비기한. 첫 연장 시에만 기록되고 이후 불변.</li>
 *   <li>{@code extensionCount}: 누적 연장 횟수. 0이면 미연장, 1 이상이면 연장된 아이템.</li>
 *   <li>{@link #extend(int)}는 항상 새 불변 인스턴스를 반환한다.</li>
 * </ul>
 *
 * <h2>소비기한 미설정 (nullable)</h2>
 * 냉장고 채우기(인식) 시점에는 소비기한을 입력받지 않는다.
 * {@link #unset()}으로 생성된 인스턴스는 {@code expiresAt=null}이며,
 * {@link #isExpirationSet()}으로 설정 여부를 확인할 수 있다.
 * {@link #isNearExpiry}, {@link #isExpired}는 미설정 시 {@code false}를 반환한다.
 *
 * <h2>파생 계산</h2>
 * <ul>
 *   <li>{@link #isShelfLifeExtended()} — {@code extensionCount > 0}</li>
 *   <li>{@link #getTotalExtendedDays()} — 연장된 경우 총 연장 일수</li>
 * </ul>
 *
 * @author 승훈
 * @since 2026-04-26
 * @see com.refridge.fridge_management.fridge.domain.Fridge#extend(String, int, LocalDate)
 * @see com.refridge.fridge_management.fridge.domain.Fridge#registerNearExpiryEvents(LocalDate, int)
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpirationInfo {

    /**
     * 현재 소비기한.
     * nullable — 소비기한 미설정 상태({@link #unset()})에서는 null.
     * 연장 시 이 값이 갱신된다.
     */
    private LocalDate expiresAt;

    /**
     * 최초 소비기한.
     * 첫 번째 연장 시점에 {@code expiresAt} 값을 복사해 기록한다.
     * 이후 추가 연장이 발생해도 이 값은 변경되지 않는다.
     * 미연장({@code extensionCount=0}) 상태에서는 null.
     */
    private LocalDate originalExpiresAt;

    /**
     * 누적 연장 횟수.
     * 0: 미연장, 1 이상: 연장된 아이템.
     */
    private int extensionCount;

    private ExpirationInfo(LocalDate expiresAt, LocalDate originalExpiresAt, int extensionCount) {
        this.expiresAt         = expiresAt;
        this.originalExpiresAt = originalExpiresAt;
        this.extensionCount    = extensionCount;
    }

    // ── 팩토리 ────────────────────────────────────────────────────────

    /**
     * 소비기한 설정 — 채우기 이후 소비기한 등록 시 사용.
     *
     * @param expiresAt 소비기한 (not null)
     */
    public static ExpirationInfo of(LocalDate expiresAt) {
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        return new ExpirationInfo(expiresAt, null, 0);
    }

    /**
     * 소비기한 미설정 — 냉장고 채우기(인식) 시점 초기값.
     * 이후 {@code Fridge.registerExpiration()}으로 소비기한을 등록해야 한다.
     */
    public static ExpirationInfo unset() {
        return new ExpirationInfo(null, null, 0);
    }

    // ── 연장 ──────────────────────────────────────────────────────────

    /**
     * 소비기한 연장 — 새 불변 인스턴스 반환.
     *
     * <h3>연장 횟수 제한 없음</h3>
     * 사용자가 냉동 미개봉 보관 등으로 실제 소비 가능 상태를 직접 판단해 반복 연장할 수 있다.
     *
     * <h3>originalExpiresAt 기록 규칙</h3>
     * 첫 번째 연장 시에만 현재 {@code expiresAt}을 {@code originalExpiresAt}으로 기록.
     * 이후 연장에서는 {@code originalExpiresAt}이 변경되지 않는다.
     *
     * @param additionalDays 연장 일수 (양의 정수)
     * @throws IllegalStateException    소비기한이 설정되지 않은 상태에서 연장 시도
     * @throws IllegalArgumentException additionalDays ≤ 0
     */
    public ExpirationInfo extend(int additionalDays) {
        if (expiresAt == null)
            throw new IllegalStateException("소비기한이 설정되지 않은 아이템은 연장할 수 없습니다.");
        if (additionalDays <= 0)
            throw new IllegalArgumentException("연장 일수는 양의 정수여야 합니다: " + additionalDays);

        // 첫 번째 연장 시에만 originalExpiresAt 기록
        LocalDate original = (extensionCount == 0) ? expiresAt : this.originalExpiresAt;

        return new ExpirationInfo(
                expiresAt.plusDays(additionalDays),
                original,
                extensionCount + 1
        );
    }

    /**
     * 소비기한 직접 등록 (채우기 이후 사용자 입력).
     * 이미 소비기한이 설정된 경우도 덮어쓰기를 허용한다 (수정 가능).
     * 연장 이력은 초기화하지 않는다 — 연장 후 날짜를 수정하는 케이스 고려.
     *
     * @param expiresAt 등록할 소비기한 (not null)
     */
    public ExpirationInfo withExpiresAt(LocalDate expiresAt) {
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        return new ExpirationInfo(expiresAt, this.originalExpiresAt, this.extensionCount);
    }

    // ── 쿼리 ──────────────────────────────────────────────────────────

    /** 소비기한이 설정되어 있는지 여부 */
    public boolean isExpirationSet() {
        return expiresAt != null;
    }

    /** 한 번이라도 연장된 아이템인지 여부 */
    public boolean isShelfLifeExtended() {
        return extensionCount > 0;
    }

    /**
     * 총 연장 일수 (최초 소비기한 대비).
     * 미연장 상태에서는 0 반환.
     */
    public long getTotalExtendedDays() {
        if (!isShelfLifeExtended() || originalExpiresAt == null) return 0L;
        return ChronoUnit.DAYS.between(originalExpiresAt, expiresAt);
    }

    /**
     * 소비기한까지 남은 일수.
     * 미설정 상태에서는 {@code Long.MAX_VALUE} 반환 (임박·만료 판단에서 제외).
     */
    public long daysUntilExpiry(LocalDate today) {
        if (expiresAt == null) return Long.MAX_VALUE;
        return ChronoUnit.DAYS.between(today, expiresAt);
    }

    /**
     * 소비기한 임박 여부 (0 ≤ daysUntilExpiry ≤ thresholdDays).
     * 소비기한 미설정 시 {@code false}.
     */
    public boolean isNearExpiry(LocalDate today, int thresholdDays) {
        if (expiresAt == null) return false;
        long days = daysUntilExpiry(today);
        return days >= 0 && days <= thresholdDays;
    }

    /**
     * 소비기한 초과 여부.
     * 소비기한 미설정 시 {@code false}.
     */
    public boolean isExpired(LocalDate today) {
        if (expiresAt == null) return false;
        return daysUntilExpiry(today) < 0;
    }

    // ── equals / hashCode / toString ─────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpirationInfo e)) return false;
        return extensionCount == e.extensionCount
                && Objects.equals(expiresAt, e.expiresAt)
                && Objects.equals(originalExpiresAt, e.originalExpiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expiresAt, originalExpiresAt, extensionCount);
    }

    @Override
    public String toString() {
        return "ExpirationInfo{expiresAt=%s, extensionCount=%d}".formatted(expiresAt, extensionCount);
    }
}