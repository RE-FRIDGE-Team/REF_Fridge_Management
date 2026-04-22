package com.refridge.fridge_management.fridge.domain.vo;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QFridgeMeta is a Querydsl query type for FridgeMeta
 */
@Generated("com.querydsl.codegen.DefaultEmbeddableSerializer")
public class QFridgeMeta extends BeanPath<FridgeMeta> {

    private static final long serialVersionUID = -1308461404L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QFridgeMeta fridgeMeta = new QFridgeMeta("fridgeMeta");

    public final NumberPath<Integer> activeItemCount = createNumber("activeItemCount", Integer.class);

    public final QMoney totalValue;

    public QFridgeMeta(String variable) {
        this(FridgeMeta.class, forVariable(variable), INITS);
    }

    public QFridgeMeta(Path<? extends FridgeMeta> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QFridgeMeta(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QFridgeMeta(PathMetadata metadata, PathInits inits) {
        this(FridgeMeta.class, metadata, inits);
    }

    public QFridgeMeta(Class<? extends FridgeMeta> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.totalValue = inits.isInitialized("totalValue") ? new QMoney(forProperty("totalValue")) : null;
    }

}

