package com.refridge.fridge_management.fridge.domain.vo;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QQuantity is a Querydsl query type for Quantity
 */
@Generated("com.querydsl.codegen.DefaultEmbeddableSerializer")
public class QQuantity extends BeanPath<Quantity> {

    private static final long serialVersionUID = -1922511867L;

    public static final QQuantity quantity = new QQuantity("quantity");

    public final NumberPath<java.math.BigDecimal> amount = createNumber("amount", java.math.BigDecimal.class);

    public final EnumPath<Quantity.QuantityUnit> unit = createEnum("unit", Quantity.QuantityUnit.class);

    public QQuantity(String variable) {
        super(Quantity.class, forVariable(variable));
    }

    public QQuantity(Path<? extends Quantity> path) {
        super(path.getType(), path.getMetadata());
    }

    public QQuantity(PathMetadata metadata) {
        super(Quantity.class, metadata);
    }

}

