package com.bukang.product.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QRace is a Querydsl query type for Race
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QRace extends EntityPathBase<Race> {

    private static final long serialVersionUID = -1917324195L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QRace race = new QRace("race");

    public final com.bukang.common.global.jpa.entity.QBaseIdAndTime _super = new com.bukang.common.global.jpa.entity.QBaseIdAndTime(this);

    public final DateTimePath<java.time.LocalDateTime> applicationCloseAt = createDateTime("applicationCloseAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> canceledAt = createDateTime("canceledAt", java.time.LocalDateTime.class);

    public final ListPath<RaceCourse, QRaceCourse> courses = this.<RaceCourse, QRaceCourse>createList("courses", RaceCourse.class, QRaceCourse.class, PathInits.DIRECT2);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createDate = _super.createDate;

    public final NumberPath<Integer> crewMaxCount = createNumber("crewMaxCount", Integer.class);

    public final NumberPath<Integer> crewMinCount = createNumber("crewMinCount", Integer.class);

    public final DateTimePath<java.time.LocalDateTime> crewSaleEndAt = createDateTime("crewSaleEndAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> crewSaleStartAt = createDateTime("crewSaleStartAt", java.time.LocalDateTime.class);

    public final StringPath description = createString("description");

    //inherited
    public final NumberPath<Integer> id = _super.id;

    public final StringPath idempotencyKey = createString("idempotencyKey");

    public final DateTimePath<java.time.LocalDateTime> individualOpenAt = createDateTime("individualOpenAt", java.time.LocalDateTime.class);

    public final StringPath location = createString("location");

    //inherited
    public final StringPath modelTypeCode = _super.modelTypeCode;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> modifyDate = _super.modifyDate;

    public final DateTimePath<java.time.LocalDateTime> raceDate = createDateTime("raceDate", java.time.LocalDateTime.class);

    public final DatePath<java.time.LocalDate> refundDeadline = createDate("refundDeadline", java.time.LocalDate.class);

    public final StringPath refundPolicy = createString("refundPolicy");

    public final StringPath regionSido = createString("regionSido");

    public final StringPath regionSigungu = createString("regionSigungu");

    public final DatePath<java.time.LocalDate> saleOpenDate = createDate("saleOpenDate", java.time.LocalDate.class);

    public final EnumPath<com.bukang.common.shared.race.domain.RaceSaleStatus> saleStatus = createEnum("saleStatus", com.bukang.common.shared.race.domain.RaceSaleStatus.class);

    public final DateTimePath<java.time.LocalDateTime> scheduleChangedAt = createDateTime("scheduleChangedAt", java.time.LocalDateTime.class);

    public final QProductSeller seller;

    public final StringPath title = createString("title");

    public QRace(String variable) {
        this(Race.class, forVariable(variable), INITS);
    }

    public QRace(Path<? extends Race> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QRace(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QRace(PathMetadata metadata, PathInits inits) {
        this(Race.class, metadata, inits);
    }

    public QRace(Class<? extends Race> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.seller = inits.isInitialized("seller") ? new QProductSeller(forProperty("seller"), inits.get("seller")) : null;
    }

}

