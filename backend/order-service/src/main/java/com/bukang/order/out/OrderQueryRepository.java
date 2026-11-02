package com.bukang.order.out;

import static com.bukang.order.domain.QOrder.order;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.bukang.order.domain.Order;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/**
 * 내 주문 목록 조회 (PRO-43). 조회 기간(from·to)이 각각 선택이라 조건을 QueryDSL로 조합한다
 * 최신순은 주문 ID 내림차순이고, 인덱스 idx_order_buyer(buyer_id, id)를 탄다
 */
@Repository
@RequiredArgsConstructor
public class OrderQueryRepository {
	private final JPAQueryFactory queryFactory;

	public Page<Order> findMine(long buyerId, LocalDate from, LocalDate to, Pageable pageable) {
		BooleanExpression condition = Expressions.allOf(
			order.buyerId.eq(buyerId),
			orderedFrom(from),
			orderedTo(to)
		);
		List<Order> orders = queryFactory.selectFrom(order)
			.where(condition)
			.orderBy(order.id.desc())
			.offset(pageable.getOffset())
			.limit(pageable.getPageSize())
			.fetch();
		Long total = queryFactory.select(order.count())
			.from(order)
			.where(condition)
			.fetchOne();
		return new PageImpl<>(orders, pageable, total == null ? 0 : total);
	}

	// from 날짜 00:00부터
	private BooleanExpression orderedFrom(LocalDate from) {
		return from != null ? order.createDate.goe(from.atStartOfDay()) : null;
	}

	// to 날짜 23:59:59.999…까지 (다음 날 00:00 전)
	private BooleanExpression orderedTo(LocalDate to) {
		return to != null ? order.createDate.lt(to.plusDays(1).atStartOfDay()) : null;
	}
}
