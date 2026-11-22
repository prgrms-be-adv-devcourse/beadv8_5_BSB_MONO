package com.bukang.product.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QParticipationHold is a Querydsl query type for ParticipationHold
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QParticipationHold extends EntityPathBase<ParticipationHold> {

    private static final long serialVersionUID = 2035001236L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QParticipationHold participationHold = new QParticipationHold("participationHold");

    public final com.bukang.common.global.jpa.entity.QBaseIdAndTime _super = new com.bukang.common.global.jpa.entity.QBaseIdAndTime(this);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createDate = _super.createDate;

    public final NumberPath<Integer> crewApplicationId = createNumber("crewApplicationId", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> expiresAt = createDateTime("expiresAt", java.time.LocalDateTime.class);

    //inherited
    public final NumberPath<Integer> id = _super.id;

    //inherited
    public final StringPath modelTypeCode = _super.modelTypeCode;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> modifyDate = _super.modifyDate;

    public final NumberPath<Integer> participationId = createNumber("participationId", Integer.class);

    public final QRaceCourse raceCourse;

    public final DateTimePath<java.time.LocalDateTime> releasedAt = createDateTime("releasedAt", java.time.LocalDateTime.class);

    public final EnumPath<HoldReleaseReason> releaseReason = createEnum("releaseReason", HoldReleaseReason.class);

    public final DateTimePath<java.time.LocalDateTime> soldAt = createDateTime("soldAt", java.time.LocalDateTime.class);

    public final EnumPath<HoldStatus> status = createEnum("status", HoldStatus.class);

    public QParticipationHold(String variable) {
        this(ParticipationHold.class, forVariable(variable), INITS);
    }

    public QParticipationHold(Path<? extends ParticipationHold> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QParticipationHold(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QParticipationHold(PathMetadata metadata, PathInits inits) {
        this(ParticipationHold.class, metadata, inits);
    }

    public QParticipationHold(Class<? extends ParticipationHold> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.raceCourse = inits.isInitialized("raceCourse") ? new QRaceCourse(forProperty("raceCourse"), inits.get("raceCourse")) : null;
    }

}

