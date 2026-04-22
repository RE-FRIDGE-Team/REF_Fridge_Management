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
 * <ul>
 *   <li>하나의 식품 아이템의 상태(ACTIVE→소비/폐기/소분)와 속성(수량, 가격, 유통기한)을 보관한다.</li>
 *   <li>상태 변경 메서드는 모두 package-private: 반드시 {@link Fridge}를 통해서만 호출된다.</li>
 * </ul>
 *
 * <h2>상태 머신</h2>
 * <pre>
 * ACTIVE ──consume()────────► CONSUMED      (terminal: 먹기 완료)
 *   ├──dispose()────────────► DISPOSED      (terminal: 폐기 처리)
 *   ├──markPortionedOut()──► PORTIONED_OUT  (terminal: 소분 원본, 자식 아이템 N개 생성됨)
 *   └──moveTo(section)──────► ACTIVE        (sectionType 변경만, 상태 유지)
 * </pre>
 * terminal 상태에서는 어떤 상태 전이도 불가능하다({@link #assertActive} 참조).
 *
 * <h2>생성 경로</h2>
 * <ul>
 *   <li>일반 아이템: {@link Fridge#fill} → {@link #create} (package-private)</li>
 *   <li>소분 자식: {@link Fridge#portion} → {@link #createPortioned} (package-private)</li>
 *   <li>요리 결과: {@link Fridge#cook} → {@link #create} (package-private)</li>
 * </ul>
 *
 * <h2>역정규화 필드: memberId</h2>
 * {@code member_id}를 FridgeItem에 직접 보관한다.
 * Saving BC 이벤트 발행 시 Fridge JOIN 없이 memberId를 알 수 있어야 하기 때문이다.
 *
 * <h2>fridgeId (FK 컬럼, 연관관계 없음)</h2>
 * {@code fridge_id}를 연관관계(@ManyToOne Fridge) 대신 단순 FK 컬럼으로 보관한다.
 * memberId 역정규화로 Fridge 역참조가 필요한 비즈니스 케이스가 없으므로
 * 불필요한 Hibernate 프록시 로딩을 제거한다.
 *
 * <h2>연관관계 Owner</h2>
 * FridgeItem이 FridgeSection의 연관관계 owner이다.
 * {@code fridge_section_id} FK는 이 클래스가 관리하며,
 * {@link FridgeSection#items}는 {@code mappedBy = "fridgeSection"}으로 역방향 참조한다.
 *
 * @author 승훈
 * @since 2025-04-20
 * @see Fridge#fill
 * @see Fridge#portion
 * @see ItemStatus
 * @see FridgeDomainEvent.FridgeItemConsumedEvent
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

    /**
     * member_id를 FridgeItem에 직접 보관.
     * Saving BC 이벤트 발행 시 JOIN 없이 memberId를 알 수 있어야 하므로 역정규화 허용.
     * AR(Fridge)에서 fill() 호출 시 주입.
     */
    @Column(name = "member_id", nullable = false, updatable = false, length = 36)
    private String memberId;

    /**
     * fridge_id FK 컬럼 (연관관계 없음).
     * Fridge 역참조가 필요한 비즈니스 케이스가 없으므로 단순 컬럼으로 보관한다.
     * (memberId 역정규화로 이벤트 발행에 충분)
     */
    @Column(name = "fridge_id", nullable = false, updatable = false, length = 36)
    private String fridgeId;

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
    @Column(name = "version", nullable = false)
    private Long version;

    // ── 팩토리: package-private (Fridge에서만 호출 가능) ──────────────

    /**
     * 일반 아이템 생성. {@link Fridge#fill}에서만 호출.
     * fridgeSection 파라미터 — FridgeSection.items 연관관계 owner 세팅.
     * fridgeId 파라미터 — fridge FK 컬럼 (연관관계 없음).
     */
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
        item.fridgeId         = Objects.requireNonNull(fridgeId, "fridgeId");
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
            String fridgeId,
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
        FridgeItem item = create(fridgeId, fridgeSection, memberId, groceryItemRef,
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
