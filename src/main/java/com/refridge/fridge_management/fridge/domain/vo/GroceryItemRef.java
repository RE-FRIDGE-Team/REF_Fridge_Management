package com.refridge.fridge_management.fridge.domain.vo;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/**
 * core_server GroceryItem 스냅샷 값 객체(Value Object).
 *
 * <h2>스냅샷 이유</h2>
 * core_server의 GroceryItem은 독립적으로 변경될 수 있다.
 * (예: 상품명 수정, 카테고리 재분류)
 * 냉장고 이력의 정합성을 위해 저장 시점의 정보를 비정규화하여 보관한다.
 * FK 조인 없이 이벤트에 필요한 정보를 자급자족한다.
 *
 * <h2>소분 정책 필드</h2>
 * {@code minPortionAmount}, {@code maxPortionAmount}는
 * core_server portionPolicy에서 복사된 값으로,
 * {@link com.refridge.fridge_management.fridge.domain.Fridge#portion} 시 소분 단위 검증에 사용된다.
 *
 * @author 승훈
 * @since 2025-06-01
 * @see com.refridge.fridge_management.fridge.domain.Fridge#portion
 * @see com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent.FridgeItemConsumedEvent
 */
@Embeddable
public class GroceryItemRef {

    private String groceryItemId;
    private String productId;           // nullable
    private String name;

    @Enumerated(EnumType.STRING)
    private FoodCategory category;

    private String defaultUnit;
    private Integer minPortionAmount;   // nullable — core_server portionPolicy.minPortion
    private Integer maxPortionAmount;   // nullable — core_server portionPolicy.maxPortion

    protected GroceryItemRef() {}

    private GroceryItemRef(Builder b) {
        this.groceryItemId    = Objects.requireNonNull(b.groceryItemId, "groceryItemId");
        this.productId        = b.productId;
        this.name             = Objects.requireNonNull(b.name, "name");
        this.category         = Objects.requireNonNull(b.category, "category");
        this.defaultUnit      = b.defaultUnit;
        this.minPortionAmount = b.minPortionAmount;
        this.maxPortionAmount = b.maxPortionAmount;
    }

    public static Builder builder() { return new Builder(); }

    public String getGroceryItemId()    { return groceryItemId; }
    public String getProductId()        { return productId; }
    public String getName()             { return name; }
    public FoodCategory getCategory()   { return category; }
    public String getDefaultUnit()      { return defaultUnit; }
    public Integer getMinPortionAmount(){ return minPortionAmount; }
    public Integer getMaxPortionAmount(){ return maxPortionAmount; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroceryItemRef r)) return false;
        return Objects.equals(groceryItemId, r.groceryItemId);
    }
    @Override public int hashCode() { return Objects.hash(groceryItemId); }
    @Override public String toString() {
        return "GroceryItemRef{id='%s', name='%s', category=%s}".formatted(groceryItemId, name, category);
    }

    public static class Builder {
        private String groceryItemId, productId, name, defaultUnit;
        private FoodCategory category;
        private Integer minPortionAmount, maxPortionAmount;

        public Builder groceryItemId(String v)    { groceryItemId = v;    return this; }
        public Builder productId(String v)         { productId = v;        return this; }
        public Builder name(String v)              { name = v;             return this; }
        public Builder category(FoodCategory v)    { category = v;         return this; }
        public Builder defaultUnit(String v)       { defaultUnit = v;      return this; }
        public Builder minPortionAmount(Integer v) { minPortionAmount = v; return this; }
        public Builder maxPortionAmount(Integer v) { maxPortionAmount = v; return this; }
        public GroceryItemRef build()              { return new GroceryItemRef(this); }
    }

    public enum FoodCategory {
        GRAIN("곡류"), VEGETABLE("채소류"), FRUIT("과일류"), MEAT("육류"),
        SEAFOOD("수산물"), DAIRY("유제품"), EGG("달걀"), PROCESSED("가공식품"),
        BEVERAGE("음료"), SEASONING("조미료"), COOKED("조리식품"), ETC("기타");

        private final String displayName;
        FoodCategory(String d) { displayName = d; }
        public String getDisplayName() { return displayName; }
    }
}