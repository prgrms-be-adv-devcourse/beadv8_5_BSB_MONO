package com.bukang.product.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QProductOutboxKafkaEvent is a Querydsl query type for ProductOutboxKafkaEvent
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QProductOutboxKafkaEvent extends EntityPathBase<ProductOutboxKafkaEvent> {

    private static final long serialVersionUID = -278146892L;

    public static final QProductOutboxKafkaEvent productOutboxKafkaEvent = new QProductOutboxKafkaEvent("productOutboxKafkaEvent");

    public final com.bukang.common.global.jpa.entity.QBaseIdAndTime _super = new com.bukang.common.global.jpa.entity.QBaseIdAndTime(this);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createDate = _super.createDate;

    public final StringPath eventType = createString("eventType");

    //inherited
    public final NumberPath<Integer> id = _super.id;

    //inherited
    public final StringPath modelTypeCode = _super.modelTypeCode;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> modifyDate = _super.modifyDate;

    public final DateTimePath<java.time.LocalDateTime> nextRetryDate = createDateTime("nextRetryDate", java.time.LocalDateTime.class);

    public final StringPath payload = createString("payload");

    public final NumberPath<Integer> refId = createNumber("refId", Integer.class);

    public final StringPath refType = createString("refType");

    public final NumberPath<Integer> retryCount = createNumber("retryCount", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> sentDate = createDateTime("sentDate", java.time.LocalDateTime.class);

    public final EnumPath<OutboxStatus> status = createEnum("status", OutboxStatus.class);

    public QProductOutboxKafkaEvent(String variable) {
        super(ProductOutboxKafkaEvent.class, forVariable(variable));
    }

    public QProductOutboxKafkaEvent(Path<? extends ProductOutboxKafkaEvent> path) {
        super(path.getType(), path.getMetadata());
    }

    public QProductOutboxKafkaEvent(PathMetadata metadata) {
        super(ProductOutboxKafkaEvent.class, metadata);
    }

}

