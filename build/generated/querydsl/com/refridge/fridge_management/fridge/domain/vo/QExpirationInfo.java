package com.refridge.fridge_management.fridge.domain.vo;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QExpirationInfo is a Querydsl query type for ExpirationInfo
 */
@Generated("com.querydsl.codegen.DefaultEmbeddableSerializer")
public class QExpirationInfo extends BeanPath<ExpirationInfo> {

    private static final long serialVersionUID = 162830327L;

    public static final QExpirationInfo expirationInfo = new QExpirationInfo("expirationInfo");

    public final BooleanPath expirationSet = createBoolean("expirationSet");

    public final DatePath<java.time.LocalDate> expiresAt = createDate("expiresAt", java.time.LocalDate.class);

    public final NumberPath<Integer> extensionCount = createNumber("extensionCount", Integer.class);

    public final DatePath<java.time.LocalDate> originalExpiresAt = createDate("originalExpiresAt", java.time.LocalDate.class);

    public final BooleanPath shelfLifeExtended = createBoolean("shelfLifeExtended");

    public final NumberPath<Long> totalExtendedDays = createNumber("totalExtendedDays", Long.class);

    public QExpirationInfo(String variable) {
        super(ExpirationInfo.class, forVariable(variable));
    }

    public QExpirationInfo(Path<? extends ExpirationInfo> path) {
        super(path.getType(), path.getMetadata());
    }

    public QExpirationInfo(PathMetadata metadata) {
        super(ExpirationInfo.class, metadata);
    }

}

