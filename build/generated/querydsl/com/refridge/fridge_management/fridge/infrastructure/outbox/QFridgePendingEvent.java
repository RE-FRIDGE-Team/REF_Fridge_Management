package com.refridge.fridge_management.fridge.infrastructure.outbox;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QFridgePendingEvent is a Querydsl query type for FridgePendingEvent
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QFridgePendingEvent extends EntityPathBase<FridgePendingEvent> {

    private static final long serialVersionUID = 1129608189L;

    public static final QFridgePendingEvent fridgePendingEvent = new QFridgePendingEvent("fridgePendingEvent");

    public final StringPath aggregateId = createString("aggregateId");

    public final StringPath aggregateType = createString("aggregateType");

    public final DateTimePath<java.time.Instant> createdAt = createDateTime("createdAt", java.time.Instant.class);

    public final StringPath eventId = createString("eventId");

    public final StringPath eventType = createString("eventType");

    public final StringPath lastError = createString("lastError");

    public final StringPath payload = createString("payload");

    public final DateTimePath<java.time.Instant> publishedAt = createDateTime("publishedAt", java.time.Instant.class);

    public final NumberPath<Integer> retryCount = createNumber("retryCount", Integer.class);

    public final EnumPath<FridgePendingEvent.OutboxStatus> status = createEnum("status", FridgePendingEvent.OutboxStatus.class);

    public final StringPath streamKey = createString("streamKey");

    public QFridgePendingEvent(String variable) {
        super(FridgePendingEvent.class, forVariable(variable));
    }

    public QFridgePendingEvent(Path<? extends FridgePendingEvent> path) {
        super(path.getType(), path.getMetadata());
    }

    public QFridgePendingEvent(PathMetadata metadata) {
        super(FridgePendingEvent.class, metadata);
    }

}

