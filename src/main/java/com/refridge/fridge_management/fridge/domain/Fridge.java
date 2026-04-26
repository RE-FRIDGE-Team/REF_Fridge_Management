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
 * 냉장고 내 모든 식품(FridgeItem) 생명주기를 단일 트랜잭션 경계 안에서 관리한다.
 * <ul>
 *   <li>불변식: {@code fridgeMeta.totalValue == ∑ ACTIVE FridgeItem.purchasePrice}</li>
 *   <li>불변식: {@code fridgeMeta.activeItemCount == ACTIVE FridgeItem 개수}</li>
 *   <li>회원당 냉장고는 정확히 1개.</li>
 * </ul>
 *
 * <h2>도메인 연산 진입점</h2>
 * <pre>
 *   fill(...)                       → 아이템 추가
 *   registerFillCompletedEvent(...) → 채우기 완료 이벤트 등록 (Feedback BC 연동)
 *   registerExpiration(...)         → 소비기한 등록 (채우기 이후 별도 입력)
 *   consume(id)                     → 먹기
 *   dispose(id)                     → 폐기
 *   move(id, section)               → 구역 이동
 *   portion(id, n)                  → 소분
 *   extend(id, days, today)         → 소비기한 연장 (횟수 제한 없음)
 *   cook(...)                       → 즉석 요리
 *   registerNearExpiryEvents(...)   → 임박 배치용 이벤트 등록
 * </pre>
 *
 * @author 승훈
 * @since 2025-06-01
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

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "totalValue.amount", column = @Column(name = "total_value",      nullable = false)),
            @AttributeOverride(name = "activeItemCount",   column = @Column(name = "active_item_count", nullable = false))
    })
    private FridgeMeta fridgeMeta;

    @OneToMany(mappedBy = "fridge", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @MapKey(name = "sectionType")
    private Map<SectionType, FridgeSection> sections = new EnumMap<>(SectionType.class);

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ── 팩토리 ────────────────────────────────────────────────────────

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
     *
     * <h3>소비기한 미설정</h3>
     * 채우기 시점에는 소비기한을 입력받지 않는다.
     * {@code ExpirationInfo.unset()}으로 초기화하며,
     * 이후 {@link #registerExpiration}으로 소비기한을 별도 등록한다.
     */
    public FridgeItem fill(
            GroceryItemRef groceryItemRef,
            Quantity quantity,
            Money purchasePrice,
            SectionType sectionType,
            ItemProcessingType processingType
    ) {
        FridgeSection section = requireSection(sectionType);
        FridgeItem item = FridgeItem.create(
                this.fridgeId, section, memberId,
                groceryItemRef, quantity, purchasePrice,
                ExpirationInfo.unset(), sectionType, processingType);
        section.addItem(item);
        fridgeMeta = fridgeMeta.addItem(purchasePrice);
        return item;
    }

    /**
     * 채우기 완료 이벤트 등록 (Feedback BC 연동).
     * {@link #fill} 호출 직후 동일 트랜잭션에서 호출한다.
     */
    public void registerFillCompletedEvent(
            UUID recognitionId,
            FridgeItem item,
            String finalBrandName,
            Set<UserEditedField> userEditedFields
    ) {
        Objects.requireNonNull(item, "item must not be null");
        Objects.requireNonNull(userEditedFields, "userEditedFields must not be null");
        registerEvent(FridgeDomainEvent.FridgeFillCompletedEvent.of(
                memberId, recognitionId, item.getFridgeItemId(),
                item.getGroceryItemRef(), finalBrandName,
                item.getQuantity(), item.getPurchasePrice(), userEditedFields));
    }

    // ── 소비기한 등록 ─────────────────────────────────────────────────

    /**
     * 소비기한 등록 (채우기 이후 사용자 별도 입력).
     *
     * <h3>정책</h3>
     * <ul>
     *   <li>소비기한이 미설정({@code ExpirationInfo.isExpirationSet()==false})인 아이템에 최초 등록.</li>
     *   <li>이미 설정된 경우도 수정을 허용한다 (사용자 입력 오류 정정).</li>
     *   <li>연장 이력({@code extensionCount})은 초기화하지 않는다.</li>
     * </ul>
     *
     * @param fridgeItemId 소비기한을 등록할 아이템 ID
     * @param expiresAt    등록할 소비기한
     */
    public void registerExpiration(String fridgeItemId, LocalDate expiresAt) {
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        FridgeItem item = requireActiveItem(fridgeItemId);
        item.registerExpiration(expiresAt);
        registerEvent(FridgeDomainEvent.FridgeItemExpirationRegisteredEvent.of(
                memberId, fridgeItemId, expiresAt));
    }

    // ── 먹기 ───────────────────────────────────────────────────────────

    public void consume(String fridgeItemId) {
        FridgeItem item = requireActiveItem(fridgeItemId);
        FridgeDomainEvent.FridgeItemConsumedEvent event = item.consume();
        fridgeMeta = fridgeMeta.removeItem(item.getPurchasePrice());
        registerEvent(event);
    }

    // ── 폐기 ───────────────────────────────────────────────────────────

    public void dispose(String fridgeItemId) {
        FridgeItem item = requireActiveItem(fridgeItemId);
        FridgeDomainEvent.FridgeItemDisposedEvent event = item.dispose();
        fridgeMeta = fridgeMeta.removeItem(item.getPurchasePrice());
        registerEvent(event);
    }

    // ── 구역 이동 ─────────────────────────────────────────────────────

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

    // ── 소비기한 연장 ─────────────────────────────────────────────────

    /**
     * 소비기한 연장.
     *
     * <h3>연장 횟수 제한 없음</h3>
     * 사용자가 냉동 미개봉 보관 등을 직접 판단해 반복 연장할 수 있다.
     * 한 번이라도 연장된 아이템은 {@code ExpirationInfo.isShelfLifeExtended()==true}로 확인 가능.
     *
     * <h3>적용 조건</h3>
     * 소비기한이 설정된 아이템 중 임박(7일 이내) 또는 초과된 아이템에만 적용 가능.
     * 소비기한 미설정 아이템은 먼저 {@link #registerExpiration}으로 등록 필요.
     */
    public void extend(String fridgeItemId, int additionalDays, LocalDate today) {
        FridgeItem item = requireActiveItem(fridgeItemId);

        if (!item.getExpirationInfo().isExpirationSet())
            throw new IllegalStateException(
                    "소비기한이 설정되지 않은 아이템은 연장할 수 없습니다. itemId: " + fridgeItemId);
        if (!item.isNearExpiry(today, 7) && !item.isExpired(today))
            throw new IllegalStateException(
                    "소비기한 임박 또는 초과된 아이템에만 연장 가능. itemId: " + fridgeItemId);

        LocalDate originalExpiresAt = item.getExpirationInfo().getOriginalExpiresAt() != null
                ? item.getExpirationInfo().getOriginalExpiresAt()
                : item.getExpirationInfo().getExpiresAt();

        LocalDate[] dates = item.extend(additionalDays);
        int newExtensionCount = item.getExpirationInfo().getExtensionCount();

        registerEvent(FridgeDomainEvent.FridgeItemShelfLifeExtendedEvent.of(
                memberId, fridgeItemId,
                originalExpiresAt, dates[1],
                additionalDays, newExtensionCount));
    }

    // ── 즉석 요리 ─────────────────────────────────────────────────────

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

    // ── 소비기한 임박 배치 ────────────────────────────────────────────

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

    public Map<SectionType, FridgeSection> getSections() { return Collections.unmodifiableMap(sections); }
    public FridgeSection getSection(SectionType type)    { return requireSection(type); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Fridge f)) return false;
        return Objects.equals(fridgeId, f.fridgeId);
    }
    @Override public int hashCode() { return Objects.hash(fridgeId); }
}