package com.bukang.product.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QProductMember is a Querydsl query type for ProductMember
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QProductMember extends EntityPathBase<ProductMember> {

    private static final long serialVersionUID = -877467555L;

    public static final QProductMember productMember = new QProductMember("productMember");

    public final com.bukang.common.shared.member.domain.QReplicaMember _super = new com.bukang.common.shared.member.domain.QReplicaMember(this);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createDate = _super.createDate;

    //inherited
    public final NumberPath<Integer> id = _super.id;

    //inherited
    public final StringPath modelTypeCode = _super.modelTypeCode;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> modifyDate = _super.modifyDate;

    //inherited
    public final StringPath nickname = _super.nickname;

    public QProductMember(String variable) {
        super(ProductMember.class, forVariable(variable));
    }

    public QProductMember(Path<? extends ProductMember> path) {
        super(path.getType(), path.getMetadata());
    }

    public QProductMember(PathMetadata metadata) {
        super(ProductMember.class, metadata);
    }

}

