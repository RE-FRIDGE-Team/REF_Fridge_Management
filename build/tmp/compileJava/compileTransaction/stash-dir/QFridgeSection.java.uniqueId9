package com.refridge.fridge_management.fridge.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QFridgeSection is a Querydsl query type for FridgeSection
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QFridgeSection extends EntityPathBase<FridgeSection> {

    private static final long serialVersionUID = 2091066245L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QFridgeSection fridgeSection = new QFridgeSection("fridgeSection");

    public final QFridge fridge;

    public final StringPath fridgeSectionId = createString("fridgeSectionId");

    public final ListPath<FridgeItem, QFridgeItem> items = this.<FridgeItem, QFridgeItem>createList("items", FridgeItem.class, QFridgeItem.class, PathInits.DIRECT2);

    public final EnumPath<com.refridge.fridge_management.fridge.domain.vo.SectionType> sectionType = createEnum("sectionType", com.refridge.fridge_management.fridge.domain.vo.SectionType.class);

    public QFridgeSection(String variable) {
        this(FridgeSection.class, forVariable(variable), INITS);
    }

    public QFridgeSection(Path<? extends FridgeSection> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QFridgeSection(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QFridgeSection(PathMetadata metadata, PathInits inits) {
        this(FridgeSection.class, metadata, inits);
    }

    public QFridgeSection(Class<? extends FridgeSection> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.fridge = inits.isInitialized("fridge") ? new QFridge(forProperty("fridge"), inits.get("fridge")) : null;
    }

}

