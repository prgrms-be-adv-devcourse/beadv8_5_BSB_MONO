package com.bukang.product.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QRaceCourse is a Querydsl query type for RaceCourse
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QRaceCourse extends EntityPathBase<RaceCourse> {

    private static final long serialVersionUID = 1228687064L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QRaceCourse raceCourse = new QRaceCourse("raceCourse");

    public final com.bukang.common.global.jpa.entity.QBaseIdAndTime _super = new com.bukang.common.global.jpa.entity.QBaseIdAndTime(this);

    public final EnumPath<CourseType> courseType = createEnum("courseType", CourseType.class);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createDate = _super.createDate;

    public final DateTimePath<java.time.LocalDateTime> crewClosedAt = createDateTime("crewClosedAt", java.time.LocalDateTime.class);

    public final NumberPath<Integer> crewQuota = createNumber("crewQuota", Integer.class);

    public final NumberPath<Integer> crewReserved = createNumber("crewReserved", Integer.class);

    public final NumberPath<Integer> crewSold = createNumber("crewSold", Integer.class);

    public final NumberPath<java.math.BigDecimal> distanceKm = createNumber("distanceKm", java.math.BigDecimal.class);

    //inherited
    public final NumberPath<Integer> id = _super.id;

    public final NumberPath<Integer> individualQuota = createNumber("individualQuota", Integer.class);

    public final NumberPath<Integer> individualReserved = createNumber("individualReserved", Integer.class);

    public final NumberPath<Integer> individualSold = createNumber("individualSold", Integer.class);

    public final BooleanPath kitIncluded = createBoolean("kitIncluded");

    public final NumberPath<Integer> kitPrice = createNumber("kitPrice", Integer.class);

    public final NumberPath<Integer> maxAge = createNumber("maxAge", Integer.class);

    public final NumberPath<Integer> minAge = createNumber("minAge", Integer.class);

    //inherited
    public final StringPath modelTypeCode = _super.modelTypeCode;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> modifyDate = _super.modifyDate;

    public final NumberPath<Integer> movedQuota = createNumber("movedQuota", Integer.class);

    public final StringPath name = createString("name");

    public final NumberPath<Integer> price = createNumber("price", Integer.class);

    public final QRace race;

    public final EnumPath<CourseStatus> status = createEnum("status", CourseStatus.class);

    public final EnumPath<CourseSurface> surface = createEnum("surface", CourseSurface.class);

    public final NumberPath<Integer> timeLimitMinutes = createNumber("timeLimitMinutes", Integer.class);

    public final ListPath<TshirtSize, EnumPath<TshirtSize>> tshirtSizeOption = this.<TshirtSize, EnumPath<TshirtSize>>createList("tshirtSizeOption", TshirtSize.class, EnumPath.class, PathInits.DIRECT2);

    public QRaceCourse(String variable) {
        this(RaceCourse.class, forVariable(variable), INITS);
    }

    public QRaceCourse(Path<? extends RaceCourse> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QRaceCourse(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QRaceCourse(PathMetadata metadata, PathInits inits) {
        this(RaceCourse.class, metadata, inits);
    }

    public QRaceCourse(Class<? extends RaceCourse> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.race = inits.isInitialized("race") ? new QRace(forProperty("race"), inits.get("race")) : null;
    }

}

