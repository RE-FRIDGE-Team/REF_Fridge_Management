package com.refridge.fridge_management.fridge.domain;

import com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent;
import com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent.ItemProcessingType;
import com.refridge.fridge_management.fridge.domain.vo.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 냉장고 아이템 Entity.
 *
 * <h2>책임</h2>
 * 하나의 식품 아이템의 상태(ACTIVE→소비/폐기/소분)와
 * 속성(수량, 가격, 소비기한)을 보관한다.
 * 상태 변경 메서드는 모두 package-private — 반드시 {@link Fridge}를 통해서만 호출된다.
 *
 * <h2>상태 머신</h2>
 * <pre>
 * ACTIVE ──consume()──────────► CONSUMED      (terminal)
 *   ├──dispose()─────────────► DISPOSED      (terminal)
 *   ├──markPortionedOut()────► PORTIONED_OUT (terminal)
 *   └──moveTo(section)───────► ACTIVE        (sectionType 변경만)
 * </pre>
 *
 * <h2>소비기한 정책</h2>
 * 채우기 시점에는 {@code ExpirationInfo.unset()}으로 초기화된다.
 * 이후 {@code Fridge.registerExpiration()}으로 소비기한을 등록한다.
 * 연장({@code extend()})은 횟수 제한 없이 가능하며,
 * {@code ExpirationInfo.isShelfLifeExtended()}로 연장 여부를 확인할 수 있다.
 *
 * @author 승훈
 * @since 2025-04-20
 */
@Entity
@Table(
        name = "fridge_item",
        schema = "fridge_schema",
        indexes = {
                @Index(name = "idx_fi_fridge_status",  columnList = "fridge_id, status"),
                @Index(name = "idx_fi_expires_at",     columnList = "expires_at"),
                @Index(name = "idx_fi_member_status",  columnList = "member_id, status, expires_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FridgeItem {

    @Id
    @Column(name = "fridge_item_id", nullable = false, updatable = false, length = 36)
    private String fridgeItemId;

    @Column(name = "member_id", nullable = false, updatable = false, length = 36)
    private String memberId;

    @Column(name = "fridge_id", nullable = false, updatable = false, length = 36)
    private String fridgeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_section_id", nullable = false, updatable = false)
    private FridgeSection fridgeSection;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "groceryItemId",    column = @Column(name = "grocery_item_id")),
            @AttributeOverride(name = "productId",        column = @Column(name = "product_id")),
            @AttributeOverride(name = "name",             column = @Column(name = "grocery_item_name")),
            @AttributeOverride(name = "category",         column = @Column(name = "grocery_item_category")),
            @AttributeOverride(name = "defaultUnit",      column = @Column(name = "grocery_item_default_unit")),
            @AttributeOverride(name = "minPortionAmount", column = @Column(name = "min_portion_amount")),
            @AttributeOverride(name = "maxPortionAmount", column = @Column(name = "max_portion_amount"))
    })
    private GroceryItemRef groceryItemRef;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "quantity_amount", nullable = false)),
            @AttributeOverride(name = "unit",   column = @Column(name = "quantity_unit",   nullable = false))
    })
    private Quantity quantity;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "purchase_price", nullable = false))
    private Money purchasePrice;

    /**
     * 소비기한 정보.
     * 채우기 시점에는 expiresAt=null(unset)으로 초기화.
     * extensionCount: 누적 연장 횟수 (0=미연장).
     * originalExpiresAt: 첫 연장 시에만 기록, 이후 불변.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "expiresAt",         column = @Column(name = "expires_at")),          // nullable
            @AttributeOverride(name = "originalExpiresAt", column = @Column(name = "original_expires_at")), // nullable
            @AttributeOverride(name = "extensionCount",    column = @Column(name = "extension_count", nullable = false))
    })
    private ExpirationInfo expirationInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_type", nullable = false, length = 20)
    private SectionType sectionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_type", nullable = false, length = 20)
    private ItemProcessingType processingType;

    @Column(name = "parent_fridge_item_id", length = 36)
    private String parentFridgeItemId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ── 팩토리: package-private ────────────────────────────────────────

    static FridgeItem create(
            String fridgeId,
            FridgeSection fridgeSection,
            String memberId,
            GroceryItemRef groceryItemRef,
            Quantity quantity,
            Money purchasePrice,
            ExpirationInfo expirationInfo,
            SectionType sectionType,
            ItemProcessingType processingType
    ) {
        FridgeItem item = new FridgeItem();
        item.fridgeItemId     = UUID.randomUUID().toString();
        item.fridgeId         = Objects.requireNonNull(fridgeId);
        item.fridgeSection    = Objects.requireNonNull(fridgeSection);
        item.memberId         = Objects.requireNonNull(memberId);
        item.groceryItemRef   = Objects.requireNonNull(groceryItemRef);
        item.quantity         = Objects.requireNonNull(quantity);
        item.purchasePrice    = Objects.requireNonNull(purchasePrice);
        item.expirationInfo   = Objects.requireNonNull(expirationInfo);
        item.sectionType      = Objects.requireNonNull(sectionType);
        item.processingType   = Objects.requireNonNull(processingType);
        item.status           = ItemStatus.ACTIVE;
        item.parentFridgeItemId = null;
        return item;
    }

    static FridgeItem createPortioned(
            String fridgeId, FridgeSection fridgeSection, String memberId,
            String parentId, GroceryItemRef groceryItemRef,
            Quantity qty, Money price, ExpirationInfo expInfo,
            SectionType section, ItemProcessingType procType
    ) {
        FridgeItem item = create(fridgeId, fridgeSection, memberId, groceryItemRef,
                qty, price, expInfo, section, procType);
        item.parentFridgeItemId = Objects.requireNonNull(parentId);
        return item;
    }

    // ── 상태 머신: package-private ─────────────────────────────────────

    FridgeDomainEvent.FridgeItemConsumedEvent consume() {
        assertActive("consume");
        this.status = ItemStatus.CONSUMED;
        return FridgeDomainEvent.FridgeItemConsumedEvent.of(
                memberId, fridgeItemId, groceryItemRef, quantity, purchasePrice, processingType);
    }

    FridgeDomainEvent.FridgeItemDisposedEvent dispose() {
        assertActive("dispose");
        this.status = ItemStatus.DISPOSED;
        return FridgeDomainEvent.FridgeItemDisposedEvent.of(
                memberId, fridgeItemId, groceryItemRef, quantity, purchasePrice);
    }

    void markPortionedOut() {
        assertActive("portion");
        this.status = ItemStatus.PORTIONED_OUT;
    }

    SectionType moveTo(SectionType newSection) {
        assertActive("move");
        if (this.sectionType == newSection)
            throw new IllegalArgumentException(
                    "이미 %s 구역에 있습니다.".formatted(newSection.getDisplayName()));
        SectionType prev = this.sectionType;
        this.sectionType = newSection;
        return prev;
    }

    /**
     * 소비기한 연장 — 횟수 제한 없음.
     * ExpirationInfo 불변 교체 패턴.
     *
     * @return [0]=연장 전 expiresAt, [1]=연장 후 expiresAt
     */
    LocalDate[] extend(int additionalDays) {
        assertActive("extend");
        LocalDate before = expirationInfo.getExpiresAt();
        this.expirationInfo = expirationInfo.extend(additionalDays);
        return new LocalDate[]{before, expirationInfo.getExpiresAt()};
    }

    /**
     * 소비기한 등록 (채우기 이후 별도 입력).
     * 미설정 → 설정, 또는 기존 값 수정 모두 허용.
     */
    void registerExpiration(LocalDate expiresAt) {
        assertActive("registerExpiration");
        this.expirationInfo = expirationInfo.withExpiresAt(expiresAt);
    }

    // ── 쿼리 (public) ─────────────────────────────────────────────────

    public boolean isActive()                            { return status.isActive(); }
    public boolean isNearExpiry(LocalDate t, int days)   { return expirationInfo.isNearExpiry(t, days); }
    public boolean isExpired(LocalDate today)            { return expirationInfo.isExpired(today); }

    // ── private ───────────────────────────────────────────────────────

    private void assertActive(String op) {
        if (!status.isActive())
            throw new IllegalStateException(
                    "[%s] ACTIVE 아이템에만 적용 가능. 현재 상태: %s, itemId: %s"
                            .formatted(op, status, fridgeItemId));
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FridgeItem i)) return false;
        return Objects.equals(fridgeItemId, i.fridgeItemId);
    }
    @Override public int hashCode() { return Objects.hash(fridgeItemId); }
}