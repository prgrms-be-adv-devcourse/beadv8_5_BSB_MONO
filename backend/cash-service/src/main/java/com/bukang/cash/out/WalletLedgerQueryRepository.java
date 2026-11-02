package com.bukang.cash.out;

import static com.bukang.cash.domain.wallet.QWalletLedger.walletLedger;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.bukang.cash.domain.wallet.LedgerCategory;
import com.bukang.cash.domain.wallet.WalletLedger;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/**
 * 예치금 내역 조회 (PRO-258). 탭(category) 필터가 선택이라 조건을 QueryDSL로 조합한다. 최신순은 ID 내림차순
 */
@Repository
@RequiredArgsConstructor
public class WalletLedgerQueryRepository {
	private final JPAQueryFactory queryFactory;

	public Page<WalletLedger> findByWallet(long walletId, LedgerCategory category, Pageable pageable) {
		BooleanExpression condition = walletLedger.wallet.id.eq(walletId)
			.and(walletLedger.ledgerType.in(
				category != null ? category.ledgerTypes() : LedgerCategory.allLedgerTypes()));
		List<WalletLedger> ledgers = queryFactory.selectFrom(walletLedger)
			.where(condition)
			.orderBy(walletLedger.id.desc())
			.offset(pageable.getOffset())
			.limit(pageable.getPageSize())
			.fetch();
		Long total = queryFactory.select(walletLedger.count())
			.from(walletLedger)
			.where(condition)
			.fetchOne();
		return new PageImpl<>(ledgers, pageable, total == null ? 0 : total);
	}
}
