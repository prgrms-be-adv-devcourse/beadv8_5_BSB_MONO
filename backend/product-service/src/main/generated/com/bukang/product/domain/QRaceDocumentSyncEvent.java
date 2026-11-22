package com.bukang.product.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QRaceDocumentSyncEvent is a Querydsl query type for RaceDocumentSyncEvent
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QRaceDocumentSyncEvent extends EntityPathBase<RaceDocumentSyncEvent> {

    private static final long serialVersionUID = 925769479L;

    public static final QRaceDocumentSyncEvent raceDocumentSyncEvent = new QRaceDocumentSyncEvent("raceDocumentSyncEvent");

    public final com.bukang.common.global.jpa.entity.QBaseIdAndTime _super = new com.bukang.common.global.jpa.entity.QBaseIdAndTime(this);

    public final DateTimePath<java.time.LocalDateTime> completeDate = createDateTime("completeDate", java.time.LocalDateTime.class);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createDate = _super.createDate;

    //inherited
    public final NumberPath<Integer> id = _super.id;

    //inherited
    public final StringPath modelTypeCode = _super.modelTypeCode;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> modifyDate = _super.modifyDate;

    public final DateTimePath<java.time.LocalDateTime> nextRetryDate = createDateTime("nextRetryDate", java.time.LocalDateTime.class);

    public final NumberPath<Integer> raceId = createNumber("raceId", Integer.class);

    public final NumberPath<Integer> retryCount = createNumber("retryCount", Integer.class);

    public final EnumPath<SyncStatus> status = createEnum("status", SyncStatus.class);

    public QRaceDocumentSyncEvent(String variable) {
        super(RaceDocumentSyncEvent.class, forVariable(variable));
    }

    public QRaceDocumentSyncEvent(Path<? extends RaceDocumentSyncEvent> path) {
        super(path.getType(), path.getMetadata());
    }

    public QRaceDocumentSyncEvent(PathMetadata metadata) {
        super(RaceDocumentSyncEvent.class, metadata);
    }

}

