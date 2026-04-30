package com.refridge.fridge_management.fridge.domain.history;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QFridgeItemHistory is a Querydsl query type for FridgeItemHistory
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QFridgeItemHistory extends EntityPathBase<FridgeItemHistory> {

    private static final long serialVersionUID = 1277263559L;

    public static final QFridgeItemHistory fridgeItemHistory = new QFridgeItemHistory("fridgeItemHistory");

    public final EnumPath<FridgeItemHistory.HistoryEventType> eventType = createEnum("eventType", FridgeItemHistory.HistoryEventType.class);

    public final StringPath fridgeItemId = createString("fridgeItemId");

    public final StringPath historyId = createString("historyId");

    public final StringPath memberId = createString("memberId");

    public final DateTimePath<java.time.Instant> occurredAt = createDateTime("occurredAt", java.time.Instant.class);

    public final SimplePath<tools.jackson.databind.JsonNode> payload = createSimple("payload", tools.jackson.databind.JsonNode.class);

    public QFridgeItemHistory(String variable) {
        super(FridgeItemHistory.class, forVariable(variable));
    }

    public QFridgeItemHistory(Path<? extends FridgeItemHistory> path) {
        super(path.getType(), path.getMetadata());
    }

    public QFridgeItemHistory(PathMetadata metadata) {
        super(FridgeItemHistory.class, metadata);
    }

}

