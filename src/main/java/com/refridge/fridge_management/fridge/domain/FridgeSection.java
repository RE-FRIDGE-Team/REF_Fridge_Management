package com.refridge.fridge_management.fridge.domain;

import com.refridge.fridge_management.fridge.domain.vo.SectionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 냉장고 구역 Entity.
 *
 * <h2>책임</h2>
 * <ul>
 *   <li>상온·냉장·냉동 3개 구역 중 하나를 나타내며, 해당 구역의 FridgeItem 목록을 소유한다.</li>
 *   <li>Fridge 생성 시 3개의 FridgeSection이 자동 생성된다 ({@link #create} 참조).</li>
 * </ul>
 *
 * <h2>식별자 전략</h2>
 * {@code fridgeSectionId = fridgeId + ":" + sectionType.name()}
 * UUID 없이 복합 의미 키를 사용한다 — 구역은 냉장고당 항상 3개 고정이므로 별도 생성이 불필요.
 *
 * <h2>아이템 관리 규칙</h2>
 * {@link #addItem}은 package-private이다.
 * Fridge의 비즈니스 메서드(fill, portion, cook)를 통해서만 호출된다.
 * 조회 전용 메서드:
 * <ul>
 *   <li>{@link #activeItems()} — ACTIVE 아이템 목록 (UI 렌더링용)</li>
 *   <li>{@link #allItems()} — 전체 아이템 (관리/감사 목적)</li>
 * </ul>
 *
 * @author 승훈
 * @since 2025-04-20
 * @see Fridge#create
 * @see FridgeItem
 * @see SectionType
 */
@Entity
@Table(
        name = "fridge_section",
        schema = "fridge_schema",
        indexes = @Index(name = "idx_fs_fridge_id", columnList = "fridge_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FridgeSection {

    @Id
    @Column(name = "fridge_section_id", nullable = false, updatable = false, length = 80)
    private String fridgeSectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id", nullable = false, updatable = false)
    private Fridge fridge;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_type", nullable = false, length = 20)
    private SectionType sectionType;

    /**
     * mappedBy = "fridgeSection" — FridgeItem.fridgeSection 이 연관관계 owner.
     * FridgeItem 쪽 @ManyToOne(fridgeSection)의 FK(fridge_section_id)로 관리됨.
     *
     * LAZY + @BatchSize(20): items 개수가 가변이고 조회 시나리오마다 필요 여부가 다름.
     * 여러 FridgeSection을 한 번에 로드할 때 @BatchSize가 N+1을 IN 절로 해결.
     */
    @OneToMany(mappedBy = "fridgeSection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 20)
    private List<FridgeItem> items = new ArrayList<>();

    /** package-private — Fridge.create()에서만 호출 */
    static FridgeSection create(Fridge fridge, SectionType sectionType) {
        FridgeSection s = new FridgeSection();
        s.fridgeSectionId = fridge.getFridgeId() + ":" + sectionType.name();
        s.fridge          = Objects.requireNonNull(fridge);
        s.sectionType     = Objects.requireNonNull(sectionType);
        return s;
    }

    /**
     * package-private — Fridge 메서드(fill, portion, cook)에서만 호출.
     * items 컬렉션에 추가 (JPA 양방향 일관성 유지).
     * FridgeItem.fridgeSection은 create() 팩토리에서 이미 세팅되어 있음.
     */
    void addItem(FridgeItem item) {
        items.add(item);
    }

    public List<FridgeItem> activeItems() {
        return items.stream().filter(FridgeItem::isActive).toList();
    }

    public List<FridgeItem> allItems() { return Collections.unmodifiableList(items); }

    public long activeItemCount() { return items.stream().filter(FridgeItem::isActive).count(); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FridgeSection s)) return false;
        return Objects.equals(fridgeSectionId, s.fridgeSectionId);
    }
    @Override public int hashCode() { return Objects.hash(fridgeSectionId); }
}
