package com.refridge.fridge_management.fridge.domain;

import com.refridge.fridge_management.fridge.domain.vo.SectionType;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "fridge_section", schema = "fridge_schema")
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
     */
    @OneToMany(mappedBy = "fridgeSection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FridgeItem> items = new ArrayList<>();

    protected FridgeSection() {}

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

    public String getFridgeSectionId() { return fridgeSectionId; }
    public SectionType getSectionType() { return sectionType; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FridgeSection s)) return false;
        return Objects.equals(fridgeSectionId, s.fridgeSectionId);
    }
    @Override public int hashCode() { return Objects.hash(fridgeSectionId); }
}