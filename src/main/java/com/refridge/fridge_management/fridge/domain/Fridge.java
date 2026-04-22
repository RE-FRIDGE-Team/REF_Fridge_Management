package com.refridge.fridge_management.fridge.domain;

import com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent;
import com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent.ConsumedIngredient;
import com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent.ItemProcessingType;
import com.refridge.fridge_management.fridge.domain.policy.UpwardMoveWarningPolicy;
import com.refridge.fridge_management.fridge.domain.vo.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDate;
import java.util.*;

/**
 * 냉장고 Aggregate Root.
 *
 * <h2>책임</h2>
 * <ul>
 *   <li>냉장고 내 모든 식품(FridgeItem) 생명주기를 단일 트랜잭션 경계 안에서 관리한다.</li>
 *   <li>불변식: {@code fridgeMeta.totalValue == ∑ ACTIVE FridgeItem.purchasePrice}</li>
 *   <li>불변식: {@code fridgeMeta.activeItemCount == ACTIVE 상태 FridgeItem 개수}</li>
 *   <li>회원당 냉장고는 정확히 1개 ({@code uq_fridge_member_id}).</li>
 * </ul>
 *
 * <h2>생성</h2>
 * 외부에서 new Fridge()를 직접 호출하는 것은 금지되어 있다.
 * 유일한 생성 경로는 {@link #create(String)} 팩토리 메서드이며,
 * 이 때 상온·냉장·냉동 3개 구역이 자동으로 초기화된다.
 *
 * <h2>도메인 연산 — 진입점</h2>
 * <pre>
 *   Fridge.fill(...)         → 아이템 추가 (Recognition 완료 후)
 *   Fridge.consume(id)       → 먹기 → FridgeItemConsumedEvent 발행
 *   Fridge.dispose(id)       → 폐기 → FridgeItemDisposedEvent 발행
 *   Fridge.move(id, section) → 구역 이동 → FridgeItemMovedEvent 발행
 *   Fridge.portion(id, n)    → 소분 → 자식 FridgeItem n개 생성
 *   Fridge.extend(id, days)  → 유통기한 연장 (임박·만료 아이템 한정)
 *   Fridge.cook(...)         → 즉석 요리 (재료 일괄 소비 + 요리 결과 생성)
 * </pre>
 *
 * <h2>이벤트 발행</h2>
 * {@link org.springframework.data.domain.AbstractAggregateRoot}를 상속하므로
 * {@link #registerEvent(Object)} 로 등록된 이벤트는
 * {@code FridgeRepository.save()} 완료 직후 Spring ApplicationEventPublisher를 통해 자동 발행된다.
 * 이벤트 수신측: Saving BC, notification_server (Redis Stream 경유).
 *
 * <h2>하위 엔티티 접근 제어</h2>
 * {@link FridgeSection#create} 및 {@link FridgeItem#create}는 package-private이다.
 * 외부 레이어(Application Service)는 반드시 Fridge의 도메인 메서드를 통해서만 아이템을 조작해야 한다.
 *
 * @author 승훈
 * @since 2025-06-01
 * @see FridgeSection
 * @see FridgeItem
 * @see FridgeDomainEvent
 * @see com.refridge.fridge_management.fridge.domain.policy.UpwardMoveWarningPolicy
 */
@Entity
@Table(
        name = "fridge",
        schema = "fridge_schema",
        uniqueConstraints = @UniqueConstraint(name = "uq_fridge_member_id", columnNames = "member_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fridge extends AbstractAggregateRoot<Fridge> {

    private static final UpwardMoveWarningPolicy UPWARD_MOVE_POLICY = new UpwardMoveWarningPolicy();

    @Id
    @Column(name = "fridge_id", nullable = false, updatable = false, length = 36)
    private String fridgeId;

    @Column(name = "member_id", nullable = false, updatable = false, unique = true, length = 36)
    private String memberId;

    /** 집계 불변식 VO — totalValue + activeItemCount */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "totalValue.amount",  column = @Column(name = "total_value",       nullable = false)),
            @AttributeOverride(name = "activeItemCount",    column = @Column(name = "active_item_count",  nullable = false))
    })
    private FridgeMeta fridgeMeta;

    /**
     * sections는 항상 3개(ROOM_TEMPERATURE / REFRIGERATED / FREEZER)이고
     * 모든 AR 도메인 메서드(fill, consume, move 등)가 반드시 접근하므로 EAGER 로딩.
     * EAGER이므로 @BatchSize 불필요 (JOIN으로 한 번에 로드).
     */
    @OneToMany(mappedBy = "fridge", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @MapKey(name = "sectionType")
    private Map<SectionType, FridgeSection> sections = new EnumMap<>(SectionType.class);

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ── 팩토리 ────────────────────────────────────────────────────────

    /**
     * 새 냉장고 생성 — 3개 구역(상온/냉장/냉동) 자동 초기화.
     * 외부에서 Fridge를 생성하는 유일한 방법.
     */
    public static Fridge create(String memberId) {
        Objects.requireNonNull(memberId, "memberId must not be null");
        Fridge fridge = new Fridge();
        fridge.fridgeId   = UUID.randomUUID().toString();
        fridge.memberId   = memberId;
        fridge.fridgeMeta = FridgeMeta.empty();
        for (SectionType type : SectionType.values()) {
            fridge.sections.put(type, FridgeSection.create(fridge, type));
        }
        return fridge;
    }

    // ── 냉장고 채우기 ──────────────────────────────────────────────────

    /**
     * 아이템 추가 (냉장고 채우기).
     * Recognition 완료 Redis Stream 이벤트 수신 후 {@code RecognitionEventConsumer}가 호출.
     *
     * @return 생성된 FridgeItem (Application 레이어에서 DTO 변환용)
     */
    public FridgeItem fill(
            GroceryItemRef groceryItemRef,
            Quantity quantity,
            Money purchasePrice,
            ExpirationInfo expirationInfo,
            SectionType sectionType,
            ItemProcessingType processingType
    ) {
        FridgeSection section = requireSection(sectionType);

        FridgeItem item = FridgeItem.create(
                this.fridgeId, section, memberId,
                groceryItemRef, quantity, purchasePrice,
                expirationInfo, sectionType, processingType);

        section.addItem(item);
        fridgeMeta = fridgeMeta.addItem(purchasePrice);
        return item;
    }

    // ── 먹기 ───────────────────────────────────────────────────────────

    /**
     * 아이템 먹기.
     * 이벤트: {@link FridgeDomainEvent.FridgeItemConsumedEvent}
     * → Saving BC가 구독해 절약액 계산.
     */
    public void consume(String fridgeItemId) {
        FridgeItem item = requireActiveItem(fridgeItemId);
        FridgeDomainEvent.FridgeItemConsumedEvent event = item.consume();
        fridgeMeta = fridgeMeta.removeItem(item.getPurchasePrice());
        registerEvent(event);
    }

    // ── 폐기 ───────────────────────────────────────────────────────────

    /**
     * 아이템 폐기.
     * 이벤트: {@link FridgeDomainEvent.FridgeItemDisposedEvent}
     * → Saving BC가 "낭비된 식비" 누적.
     */
    public void dispose(String fridgeItemId) {
        FridgeItem item = requireActiveItem(fridgeItemId);
        FridgeDomainEvent.FridgeItemDisposedEvent event = item.dispose();
        fridgeMeta = fridgeMeta.removeItem(item.getPurchasePrice());
        registerEvent(event);
    }

    // ── 구역 이동 ─────────────────────────────────────────────────────

    /**
     * 구역 간 이동.
     * 이벤트: {@link FridgeDomainEvent.FridgeItemMovedEvent}
     * wasUpwardMove=true → UI가 경고 표시.
     */
    public void move(String fridgeItemId, SectionType targetSection) {
        FridgeItem item  = requireActiveItem(fridgeItemId);
        SectionType from = item.getSectionType();
        UPWARD_MOVE_POLICY.validateMove(from, targetSection);
        boolean isUpward = UPWARD_MOVE_POLICY.isUpwardMove(from, targetSection);
        item.moveTo(targetSection);
        registerEvent(FridgeDomainEvent.FridgeItemMovedEvent.of(
                memberId, fridgeItemId, from, targetSection, isUpward));
    }

    // ── 소분 ───────────────────────────────────────────────────────────

    /**
     * 소분.
     * 원본 PORTIONED_OUT 처리 + 자식 N개 생성.
     * 이벤트: {@link FridgeDomainEvent.FridgeItemPortionedEvent}
     *
     * @return 생성된 자식 FridgeItem 목록
     */
    public List<FridgeItem> portion(String fridgeItemId, int portionCount) {
        if (portionCount < 2)
            throw new IllegalArgumentException("소분 수는 2 이상이어야 합니다: " + portionCount);

        FridgeItem original = requireActiveItem(fridgeItemId);
        validatePortionPolicy(original, portionCount);

        original.markPortionedOut();
        fridgeMeta = fridgeMeta.removeItem(original.getPurchasePrice());

        Quantity portionQty = original.getQuantity().divideBy(portionCount);
        Money portionPrice  = original.getPurchasePrice()
                .proportionalTo(portionQty.getAmount(), original.getQuantity().getAmount());

        FridgeSection section = requireSection(original.getSectionType());

        List<FridgeItem> portionedItems = new ArrayList<>(portionCount);
        for (int i = 0; i < portionCount; i++) {
            FridgeItem child = FridgeItem.createPortioned(
                    this.fridgeId, section, memberId, fridgeItemId,
                    original.getGroceryItemRef(), portionQty, portionPrice,
                    original.getExpirationInfo(), original.getSectionType(), original.getProcessingType());
            section.addItem(child);
            portionedItems.add(child);
            fridgeMeta = fridgeMeta.addItem(portionPrice);
        }

        registerEvent(FridgeDomainEvent.FridgeItemPortionedEvent.of(memberId, fridgeItemId, portionCount));
        return portionedItems;
    }

    // ── 기한 연장 ─────────────────────────────────────────────────────

    /**
     * 기한 연장.
     * 유통기한 임박(7일 이내) 또는 만료된 아이템에만 적용.
     * additionalDays는 ShelfLifeAdvisorPort(RAG)의 추천값.
     */
    public void extend(String fridgeItemId, int additionalDays, LocalDate today) {
        FridgeItem item = requireActiveItem(fridgeItemId);
        if (!item.isNearExpiry(today, 7) && !item.isExpired(today))
            throw new IllegalStateException(
                    "유통기한 임박 또는 만료된 아이템에만 연장 가능. itemId: " + fridgeItemId);

        LocalDate[] dates = item.extend(additionalDays);
        registerEvent(FridgeDomainEvent.FridgeItemShelfLifeExtendedEvent.of(
                memberId, fridgeItemId, dates[0], dates[1], additionalDays));
    }

    // ── 즉석 요리 ─────────────────────────────────────────────────────

    /**
     * 즉석 요리.
     * 재료 일괄 소비 + 요리 결과 아이템 생성, 단일 트랜잭션.
     * 이벤트에 consumedIngredients 스냅샷 포함 → Saving BC가 재료별 집계 가능.
     */
    public FridgeItem cook(
            List<String> ingredientItemIds,
            GroceryItemRef cookedGroceryRef,
            ExpirationInfo cookedExpiresAt,
            int servings,
            SectionType targetSection
    ) {
        if (ingredientItemIds == null || ingredientItemIds.isEmpty())
            throw new IllegalArgumentException("요리 재료를 1개 이상 선택해야 합니다.");

        Money totalPrice = Money.ZERO;
        List<ConsumedIngredient> ingredients = new ArrayList<>(ingredientItemIds.size());

        for (String id : ingredientItemIds) {
            FridgeItem ing = requireActiveItem(id);
            ing.consume();
            totalPrice = totalPrice.add(ing.getPurchasePrice());
            fridgeMeta = fridgeMeta.removeItem(ing.getPurchasePrice());
            ingredients.add(new ConsumedIngredient(id, ing.getGroceryItemRef(), ing.getPurchasePrice()));
        }

        FridgeSection section = requireSection(targetSection);
        Quantity cookedQty    = Quantity.of(servings, Quantity.QuantityUnit.SERVING);
        FridgeItem cookedItem = FridgeItem.create(
                this.fridgeId, section, memberId,
                cookedGroceryRef, cookedQty, totalPrice,
                cookedExpiresAt, targetSection, ItemProcessingType.COOKED);

        section.addItem(cookedItem);
        fridgeMeta = fridgeMeta.addItem(totalPrice);

        registerEvent(FridgeDomainEvent.FridgeItemCookedEvent.of(
                memberId, cookedItem.getFridgeItemId(), ingredients, servings));
        return cookedItem;
    }

    // ── 유통기한 임박 배치 ────────────────────────────────────────────

    /**
     * 임박 아이템 이벤트 등록 (배치 스케줄러 → notification_server 연동).
     */
    public void registerNearExpiryEvents(LocalDate today, int thresholdDays) {
        sections.values().stream()
                .flatMap(s -> s.activeItems().stream())
                .filter(item -> item.isNearExpiry(today, thresholdDays))
                .forEach(item -> registerEvent(FridgeDomainEvent.FridgeItemNearExpiryEvent.of(
                        memberId, item.getFridgeItemId(),
                        item.getGroceryItemRef(),
                        item.getExpirationInfo().getExpiresAt(),
                        item.getExpirationInfo().daysUntilExpiry(today),
                        item.getSectionType())));
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────

    private FridgeSection requireSection(SectionType type) {
        FridgeSection s = sections.get(type);
        if (s == null)
            throw new IllegalStateException("구역 없음: " + type + ", fridgeId: " + fridgeId);
        return s;
    }

    private FridgeItem requireActiveItem(String fridgeItemId) {
        return sections.values().stream()
                .flatMap(s -> s.allItems().stream())
                .filter(i -> i.getFridgeItemId().equals(fridgeItemId) && i.isActive())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "ACTIVE 아이템 없음: fridgeItemId=" + fridgeItemId));
    }

    private void validatePortionPolicy(FridgeItem item, int portionCount) {
        Integer min = item.getGroceryItemRef().getMinPortionAmount();
        if (min != null) {
            int portionAmount = item.getQuantity().getAmount().intValue() / portionCount;
            if (portionAmount < min)
                throw new IllegalArgumentException(
                        "소분 단위(%dg)가 최소 소분 단위(%dg) 미만.".formatted(portionAmount, min));
        }
    }

    public Map<SectionType, FridgeSection> getSections() {
        return Collections.unmodifiableMap(sections);
    }

    public FridgeSection getSection(SectionType type) { return requireSection(type); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Fridge f)) return false;
        return Objects.equals(fridgeId, f.fridgeId);
    }
    @Override public int hashCode() { return Objects.hash(fridgeId); }
}
