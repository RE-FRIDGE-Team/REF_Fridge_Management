package com.refridge.fridge_management.fridge.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QFridgeItem is a Querydsl query type for FridgeItem
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QFridgeItem extends EntityPathBase<FridgeItem> {

    private static final long serialVersionUID = -1592714637L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QFridgeItem fridgeItem = new QFridgeItem("fridgeItem");

    public final com.refridge.fridge_management.fridge.domain.vo.QExpirationInfo expirationInfo;

    public final QFridge fridge;

    public final StringPath fridgeItemId = createString("fridgeItemId");

    public final QFridgeSection fridgeSection;

    public final com.refridge.fridge_management.fridge.domain.vo.QGroceryItemRef groceryItemRef;

    public final StringPath memberId = createString("memberId");

    public final StringPath parentFridgeItemId = createString("parentFridgeItemId");

    public final EnumPath<com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent.ItemProcessingType> processingType = createEnum("processingType", com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent.ItemProcessingType.class);

    public final com.refridge.fridge_management.fridge.domain.vo.QMoney purchasePrice;

    public final com.refridge.fridge_management.fridge.domain.vo.QQuantity quantity;

    public final EnumPath<com.refridge.fridge_management.fridge.domain.vo.SectionType> sectionType = createEnum("sectionType", com.refridge.fridge_management.fridge.domain.vo.SectionType.class);

    public final EnumPath<com.refridge.fridge_management.fridge.domain.vo.ItemStatus> status = createEnum("status", com.refridge.fridge_management.fridge.domain.vo.ItemStatus.class);

    public final NumberPath<Long> version = createNumber("version", Long.class);

    public QFridgeItem(String variable) {
        this(FridgeItem.class, forVariable(variable), INITS);
    }

    public QFridgeItem(Path<? extends FridgeItem> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QFridgeItem(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QFridgeItem(PathMetadata metadata, PathInits inits) {
        this(FridgeItem.class, metadata, inits);
    }

    public QFridgeItem(Class<? extends FridgeItem> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.expirationInfo = inits.isInitialized("expirationInfo") ? new com.refridge.fridge_management.fridge.domain.vo.QExpirationInfo(forProperty("expirationInfo")) : null;
        this.fridge = inits.isInitialized("fridge") ? new QFridge(forProperty("fridge"), inits.get("fridge")) : null;
        this.fridgeSection = inits.isInitialized("fridgeSection") ? new QFridgeSection(forProperty("fridgeSection"), inits.get("fridgeSection")) : null;
        this.groceryItemRef = inits.isInitialized("groceryItemRef") ? new com.refridge.fridge_management.fridge.domain.vo.QGroceryItemRef(forProperty("groceryItemRef")) : null;
        this.purchasePrice = inits.isInitialized("purchasePrice") ? new com.refridge.fridge_management.fridge.domain.vo.QMoney(forProperty("purchasePrice")) : null;
        this.quantity = inits.isInitialized("quantity") ? new com.refridge.fridge_management.fridge.domain.vo.QQuantity(forProperty("quantity")) : null;
    }

}

