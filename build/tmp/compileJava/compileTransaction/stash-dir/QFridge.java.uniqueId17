package com.refridge.fridge_management.fridge.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QFridge is a Querydsl query type for Fridge
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QFridge extends EntityPathBase<Fridge> {

    private static final long serialVersionUID = -1689924800L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QFridge fridge = new QFridge("fridge");

    public final StringPath fridgeId = createString("fridgeId");

    public final com.refridge.fridge_management.fridge.domain.vo.QFridgeMeta fridgeMeta;

    public final StringPath memberId = createString("memberId");

    public final MapPath<com.refridge.fridge_management.fridge.domain.vo.SectionType, FridgeSection, QFridgeSection> sections = this.<com.refridge.fridge_management.fridge.domain.vo.SectionType, FridgeSection, QFridgeSection>createMap("sections", com.refridge.fridge_management.fridge.domain.vo.SectionType.class, FridgeSection.class, QFridgeSection.class);

    public final NumberPath<Long> version = createNumber("version", Long.class);

    public QFridge(String variable) {
        this(Fridge.class, forVariable(variable), INITS);
    }

    public QFridge(Path<? extends Fridge> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QFridge(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QFridge(PathMetadata metadata, PathInits inits) {
        this(Fridge.class, metadata, inits);
    }

    public QFridge(Class<? extends Fridge> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.fridgeMeta = inits.isInitialized("fridgeMeta") ? new com.refridge.fridge_management.fridge.domain.vo.QFridgeMeta(forProperty("fridgeMeta"), inits.get("fridgeMeta")) : null;
    }

}

