package com.bukang.product.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QProductSeller is a Querydsl query type for ProductSeller
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QProductSeller extends EntityPathBase<ProductSeller> {

    private static final long serialVersionUID = -705712830L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QProductSeller productSeller = new QProductSeller("productSeller");

    public final com.bukang.common.global.jpa.entity.QBaseEntity _super = new com.bukang.common.global.jpa.entity.QBaseEntity(this);

    public final StringPath businessName = createString("businessName");

    public final StringPath businessNumber = createString("businessNumber");

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createDate = _super.createDate;

    public final NumberPath<Integer> id = createNumber("id", Integer.class);

    public final QProductMember member;

    //inherited
    public final StringPath modelTypeCode = _super.modelTypeCode;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> modifyDate = _super.modifyDate;

    public final StringPath representativeName = createString("representativeName");

    public final DateTimePath<java.time.LocalDateTime> requestedAt = createDateTime("requestedAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> reviewedAt = createDateTime("reviewedAt", java.time.LocalDateTime.class);

    public final EnumPath<com.bukang.common.shared.seller.domain.SellerType> sellerType = createEnum("sellerType", com.bukang.common.shared.seller.domain.SellerType.class);

    public final EnumPath<com.bukang.common.shared.seller.domain.SellerStatus> status = createEnum("status", com.bukang.common.shared.seller.domain.SellerStatus.class);

    public QProductSeller(String variable) {
        this(ProductSeller.class, forVariable(variable), INITS);
    }

    public QProductSeller(Path<? extends ProductSeller> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QProductSeller(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QProductSeller(PathMetadata metadata, PathInits inits) {
        this(ProductSeller.class, metadata, inits);
    }

    public QProductSeller(Class<? extends ProductSeller> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.member = inits.isInitialized("member") ? new QProductMember(forProperty("member")) : null;
    }

}

