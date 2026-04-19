package com.refridge.fridge_management.fridge.domain.vo;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QGroceryItemRef is a Querydsl query type for GroceryItemRef
 */
@Generated("com.querydsl.codegen.DefaultEmbeddableSerializer")
public class QGroceryItemRef extends BeanPath<GroceryItemRef> {

    private static final long serialVersionUID = -1797741907L;

    public static final QGroceryItemRef groceryItemRef = new QGroceryItemRef("groceryItemRef");

    public final EnumPath<GroceryItemRef.FoodCategory> category = createEnum("category", GroceryItemRef.FoodCategory.class);

    public final StringPath defaultUnit = createString("defaultUnit");

    public final StringPath groceryItemId = createString("groceryItemId");

    public final NumberPath<Integer> maxPortionAmount = createNumber("maxPortionAmount", Integer.class);

    public final NumberPath<Integer> minPortionAmount = createNumber("minPortionAmount", Integer.class);

    public final StringPath name = createString("name");

    public final StringPath productId = createString("productId");

    public QGroceryItemRef(String variable) {
        super(GroceryItemRef.class, forVariable(variable));
    }

    public QGroceryItemRef(Path<? extends GroceryItemRef> path) {
        super(path.getType(), path.getMetadata());
    }

    public QGroceryItemRef(PathMetadata metadata) {
        super(GroceryItemRef.class, metadata);
    }

}

