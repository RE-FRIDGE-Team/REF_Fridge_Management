package com.refridge.fridge_management.fridge.domain;

import com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent;
import com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent.ItemProcessingType;
import com.refridge.fridge_management.fridge.domain.vo.*;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 냉장고 아이템 Entity.
 *
 * ── 생성 보호 ────────────────────────────────────────────────────────
 * 외부에서 직접 new FridgeItem() 또는 FridgeItem.create()를 호출할 수 없다.
 * 유일한 진입점은 {@link Fridge#fill}과 {@link Fridge#portion}.
 * 이를 위해:
 *  - JPA용 기본 생성자: protected (JPA 프록시는 같은 패키지 or 서브클래스)
 *  - create() 팩토리: package-private (같은 도메인 패키지인 Fridge만 호출 가능)
 *  - 상태 변경 메서드(consume, dispose, ...): package-private (Fridge에서만 호출)
 *
 * ── 연관관계 owner ────────────────────────────────────────────────────
 * FridgeItem이 FridgeSection의 연관관계 owner.
 * FridgeSection.items는 mappedBy = "fridgeSection" 으로 역방향 참조.
 * FridgeItem은 fridge(Fridge)와 fridgeSection(FridgeSection) 둘 다 보유:
 *  - fridge: 이벤트/쿼리에서 Fridge 직접 참조 필요 시
 *  - fridgeSection: JPA 연관관계 owner (FK = fridge_section_id)
 *
 * ── 상태 머신 ────────────────────────────────────────────────────────
 * ACTIVE ──consume()────> CONSUMED      (terminal)
 *   ├──dispose()────────> DISPOSED      (terminal)
 *   ├──markPortionedOut()> PORTIONED_OUT (terminal, Fridge가 자식 생성)
 *   └──moveTo(section)──> ACTIVE        (sectionType만 변경)
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
public class FridgeItem {

    @Id
    @Column(name = "fridge_item_id", nullable = false, updatable = false, length = 36)
    private String fridgeItemId;

    /**
     * member_id를 FridgeItem에 직접 보관.
     * Saving BC 이벤트 발행 시 JOIN 없이 memberId를 알 수 있어야 하므로 역정규화 허용.
     * AR(Fridge)에서 fill() 호출 시 주입.
     */
    @Column(name = "member_id", nullable = false, updatable = false, length = 36)
    private String memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id", nullable = false, updatable = false)
    private Fridge fridge;

    /**
     * FridgeSection 연관관계 owner.
     * FridgeSection.items의 mappedBy = "fridgeSection" 과 대응.
     */
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

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "manufacturedAt",    column = @Column(name = "manufactured_at")),
            @AttributeOverride(name = "expiresAt",         column = @Column(name = "expires_at",          nullable = false)),
            @AttributeOverride(name = "shelfLifeExtended", column = @Column(name = "shelf_life_extended",  nullable = false)),
            @AttributeOverride(name = "originalExpiresAt", column = @Column(name = "original_expires_at"))
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

    /** 소분 원본 아이템 ID (부모 추적용, nullable) */
    @Column(name = "parent_fridge_item_id", length = 36)
    private String parentFridgeItemId;

    @Version
    @Column(name = "version")
    private Long version;

    // ── 생성자: JPA 전용 ─────────────────────────────────────────────

    /** JPA 프록시 전용. 도메인 코드에서 직접 호출 금지. */
    protected FridgeItem() {}

    // ── 팩토리: package-private (Fridge에서만 호출 가능) ──────────────

    /**
     * 일반 아이템 생성. {@link Fridge#fill}에서만 호출.
     * fridgeSection 파라미터가 추가됨 — FridgeSection.items 연관관계 owner 세팅.
     */
    static FridgeItem create(
            Fridge fridge,
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
        item.fridge           = Objects.requireNonNull(fridge, "fridge");
        item.fridgeSection    = Objects.requireNonNull(fridgeSection, "fridgeSection");
        item.memberId         = Objects.requireNonNull(memberId, "memberId");
        item.groceryItemRef   = Objects.requireNonNull(groceryItemRef, "groceryItemRef");
        item.quantity         = Objects.requireNonNull(quantity, "quantity");
        item.purchasePrice    = Objects.requireNonNull(purchasePrice, "purchasePrice");
        item.expirationInfo   = Objects.requireNonNull(expirationInfo, "expirationInfo");
        item.sectionType      = Objects.requireNonNull(sectionType, "sectionType");
        item.processingType   = Objects.requireNonNull(processingType, "processingType");
        item.status           = ItemStatus.ACTIVE;
        item.parentFridgeItemId = null;
        return item;
    }

    /**
     * 소분 자식 아이템 생성. {@link Fridge#portion}에서만 호출.
     */
    static FridgeItem createPortioned(
            Fridge fridge,
            FridgeSection fridgeSection,
            String memberId,
            String parentId,
            GroceryItemRef groceryItemRef,
            Quantity qty,
            Money price,
            ExpirationInfo expInfo,
            SectionType section,
            ItemProcessingType procType
    ) {
        FridgeItem item = create(fridge, fridgeSection, memberId, groceryItemRef,
                qty, price, expInfo, section, procType);
        item.parentFridgeItemId = Objects.requireNonNull(parentId, "parentId");
        return item;
    }

    // ── 상태 머신: package-private (Fridge에서만 호출) ─────────────────

    /** ACTIVE → CONSUMED. 반환 이벤트를 Fridge가 registerEvent()에 등록. */
    FridgeDomainEvent.FridgeItemConsumedEvent consume() {
        assertActive("consume");
        this.status = ItemStatus.CONSUMED;
        return FridgeDomainEvent.FridgeItemConsumedEvent.of(
                memberId, fridgeItemId, groceryItemRef, quantity, purchasePrice, processingType);
    }

    /** ACTIVE → DISPOSED */
    FridgeDomainEvent.FridgeItemDisposedEvent dispose() {
        assertActive("dispose");
        this.status = ItemStatus.DISPOSED;
        return FridgeDomainEvent.FridgeItemDisposedEvent.of(
                memberId, fridgeItemId, groceryItemRef, quantity, purchasePrice);
    }

    /** ACTIVE → PORTIONED_OUT. 자식 생성은 Fridge 책임. */
    void markPortionedOut() {
        assertActive("portion");
        this.status = ItemStatus.PORTIONED_OUT;
    }

    /** sectionType 변경, ACTIVE 유지. 이전 sectionType 반환. */
    SectionType moveTo(SectionType newSection) {
        assertActive("move");
        if (this.sectionType == newSection)
            throw new IllegalArgumentException(
                    "이미 %s 구역에 있습니다.".formatted(newSection.getDisplayName()));
        SectionType prev = this.sectionType;
        this.sectionType = newSection;
        return prev;
    }

    /** ExpirationInfo 연장. 이전/새 만료일 배열 반환 (이벤트 발행용). */
    LocalDate[] extend(int additionalDays) {
        assertActive("extend");
        LocalDate original = expirationInfo.getExpiresAt();
        this.expirationInfo = expirationInfo.extend(additionalDays);
        return new LocalDate[]{original, expirationInfo.getExpiresAt()};
    }

    // ── 쿼리 (public) ─────────────────────────────────────────────────

    public boolean isActive()                            { return status.isActive(); }
    public boolean isNearExpiry(LocalDate t, int days)   { return expirationInfo.isNearExpiry(t, days); }
    public boolean isExpired(LocalDate today)            { return expirationInfo.isExpired(today); }

    // ── Getter (public) ───────────────────────────────────────────────

    public String getFridgeItemId()               { return fridgeItemId; }
    public String getMemberId()                   { return memberId; }
    public FridgeSection getFridgeSection()       { return fridgeSection; }
    public GroceryItemRef getGroceryItemRef()     { return groceryItemRef; }
    public Quantity getQuantity()                 { return quantity; }
    public Money getPurchasePrice()               { return purchasePrice; }
    public ExpirationInfo getExpirationInfo()     { return expirationInfo; }
    public SectionType getSectionType()           { return sectionType; }
    public ItemStatus getStatus()                 { return status; }
    public ItemProcessingType getProcessingType() { return processingType; }
    public String getParentFridgeItemId()         { return parentFridgeItemId; }

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