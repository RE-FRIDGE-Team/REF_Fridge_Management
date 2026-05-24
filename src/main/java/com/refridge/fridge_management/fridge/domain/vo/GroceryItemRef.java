package com.refridge.fridge_management.fridge.domain.vo;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * core_server GroceryItem 스냅샷 값 객체(Value Object).
 *
 * <h2>v4 변경점 (단위 메타데이터 3종 추가)</h2>
 * <ul>
 *   <li>{@code allowedUsageUnitsCsv} — 입력 가능 단위 코드 CSV
 *       (core_server v3와 동일 패턴 채택 — 별도 테이블 회피, 단일 컬럼)</li>
 *   <li>{@code defaultUsageUnit} — 기본 활성 단위 코드</li>
 *   <li>{@code pieceWeightGram} — 1개당 중량(g)</li>
 * </ul>
 * 이 정보는 fill 시점에 한 번 저장되며, 이후 Cook 등에서
 * core_server를 다시 호출하지 않고 활용된다.
 *
 * <h2>왜 CSV인가</h2>
 * {@code @ElementCollection}으로 별도 테이블을 두면:
 * <ul>
 *   <li>fridge_item마다 추가 JOIN 발생</li>
 *   <li>스냅샷 본질에 어긋남 (스냅샷은 자급자족해야 함)</li>
 * </ul>
 * CSV는 단일 컬럼으로 충분하고 core_server v3의 {@code REFGroceryItemUsageMetadata}와 동일 패턴.
 *
 * <h2>스냅샷 이유</h2>
 * core_server의 GroceryItem은 독립적으로 변경될 수 있다.
 * 냉장고 이력의 정합성을 위해 저장 시점의 정보를 비정규화하여 보관한다.
 *
 * <h2>소분 정책 필드</h2>
 * {@code minPortionAmount}, {@code maxPortionAmount}는
 * core_server portionPolicy에서 복사된 값으로,
 * {@link com.refridge.fridge_management.fridge.domain.Fridge#portion} 시 소분 단위 검증에 사용된다.
 *
 * @author 승훈
 * @since 2026-05-15
 * @see com.refridge.fridge_management.fridge.application.usecase.cook.UsageSpec
 */
@Embeddable
public class GroceryItemRef {

    private static final String CSV_DELIMITER = ",";

    private String groceryItemId;
    private String productId;           // nullable
    private String name;

    @Enumerated(EnumType.STRING)
    private FoodCategory category;

    /** 최종 차감 단위 (core_server REFUsageUnit BASE 코드) */
    private String defaultUnit;

    private Integer minPortionAmount;   // nullable
    private Integer maxPortionAmount;   // nullable

    /**
     * 입력 가능한 단위 코드를 CSV로 저장 (예: "EGG,GRAM" 또는 "TABLESPOON,TEASPOON,CUP,MILLILITER").
     * 도메인 로직은 {@link #getAllowedUsageUnits()}로 List를 받아 사용.
     */
    private String allowedUsageUnitsCsv;

    /** 첫 진입 시 활성화될 단위 코드 (allowedUsageUnits 중 하나) */
    private String defaultUsageUnit;

    /**
     * 1개(또는 1알·1마리·1모·1장·1캔)당 중량(g).
     * COUNT 계열 단위와 GRAM 환산에 사용.
     * 예: 계란 1알 = 60g, 두부 1모 = 300g.
     */
    private Integer pieceWeightGram;

    protected GroceryItemRef() {}

    private GroceryItemRef(Builder b) {
        this.groceryItemId        = Objects.requireNonNull(b.groceryItemId, "groceryItemId");
        this.productId            = b.productId;
        this.name                 = Objects.requireNonNull(b.name, "name");
        this.category             = Objects.requireNonNull(b.category, "category");
        this.defaultUnit          = b.defaultUnit;
        this.minPortionAmount     = b.minPortionAmount;
        this.maxPortionAmount     = b.maxPortionAmount;
        this.allowedUsageUnitsCsv = b.allowedUsageUnitsCsv;
        this.defaultUsageUnit     = b.defaultUsageUnit;
        this.pieceWeightGram      = b.pieceWeightGram;
    }

    public static Builder builder() { return new Builder(); }

    // ── Getters ──────────────────────────────────────────────────────

    public String getGroceryItemId()    { return groceryItemId; }
    public String getProductId()        { return productId; }
    public String getName()             { return name; }
    public FoodCategory getCategory()   { return category; }
    public String getDefaultUnit()      { return defaultUnit; }
    public Integer getMinPortionAmount(){ return minPortionAmount; }
    public Integer getMaxPortionAmount(){ return maxPortionAmount; }
    public String getDefaultUsageUnit() { return defaultUsageUnit; }
    public Integer getPieceWeightGram() { return pieceWeightGram; }

    /**
     * 입력 가능 단위 코드 목록.
     * CSV가 null/빈 문자열이면 빈 리스트 반환.
     */
    public List<String> getAllowedUsageUnits() {
        if (allowedUsageUnitsCsv == null || allowedUsageUnitsCsv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(allowedUsageUnitsCsv.split(CSV_DELIMITER))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** 내부 저장용 CSV (JPA 매핑 또는 테스트 검증 시 사용) */
    public String getAllowedUsageUnitsCsv() { return allowedUsageUnitsCsv; }

    // ── equals / hashCode / toString ─────────────────────────────────

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroceryItemRef r)) return false;
        return Objects.equals(groceryItemId, r.groceryItemId);
    }
    @Override public int hashCode() { return Objects.hash(groceryItemId); }
    @Override public String toString() {
        return "GroceryItemRef{id='%s', name='%s', category=%s}".formatted(groceryItemId, name, category);
    }

    // ── Builder ──────────────────────────────────────────────────────

    public static class Builder {
        private String groceryItemId, productId, name, defaultUnit;
        private FoodCategory category;
        private Integer minPortionAmount, maxPortionAmount, pieceWeightGram;
        private String allowedUsageUnitsCsv, defaultUsageUnit;

        public Builder groceryItemId(String v)    { groceryItemId = v;    return this; }
        public Builder productId(String v)         { productId = v;        return this; }
        public Builder name(String v)              { name = v;             return this; }
        public Builder category(FoodCategory v)    { category = v;         return this; }
        public Builder defaultUnit(String v)       { defaultUnit = v;      return this; }
        public Builder minPortionAmount(Integer v) { minPortionAmount = v; return this; }
        public Builder maxPortionAmount(Integer v) { maxPortionAmount = v; return this; }

        /** 단위 코드 리스트를 CSV로 직렬화하여 저장 */
        public Builder allowedUsageUnits(List<String> v) {
            this.allowedUsageUnitsCsv = (v == null || v.isEmpty())
                    ? null
                    : String.join(CSV_DELIMITER, v);
            return this;
        }
        public Builder defaultUsageUnit(String v)  { defaultUsageUnit = v;  return this; }
        public Builder pieceWeightGram(Integer v)  { pieceWeightGram = v;   return this; }

        public GroceryItemRef build()              { return new GroceryItemRef(this); }
    }

    // ── FoodCategory ─────────────────────────────────────────────────

    public enum FoodCategory {
        GRAIN("곡류"), VEGETABLE("채소류"), FRUIT("과일류"), MEAT("육류"),
        SEAFOOD("수산물"), DAIRY("유제품"), EGG("달걀"), PROCESSED("가공식품"),
        BEVERAGE("음료"), SEASONING("조미료"), COOKED("조리식품"), ETC("기타");

        private final String displayName;
        FoodCategory(String d) { displayName = d; }
        public String getDisplayName() { return displayName; }
    }
}