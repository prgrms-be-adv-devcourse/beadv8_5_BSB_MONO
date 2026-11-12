package com.bukang.cash.out.charge;

import static com.bukang.cash.domain.charge.QWalletCharge.walletCharge;
import static com.bukang.cash.domain.member.QCashMember.cashMember;
import static com.bukang.cash.domain.wallet.QWallet.wallet;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.bukang.cash.domain.charge.ChargeStatus;
import com.bukang.cash.domain.charge.WalletCharge;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 「고액 충전 확인」 목록 조회 (정책 4장 2차 2-1 「신규 회원 고액 충전 표시」)
 */
@Repository
@RequiredArgsConstructor
public class ChargeReviewQueryRepository {
	private final JPAQueryFactory queryFactory;

	// 검토 표시가 붙은 충전을 완료 시각 최신순으로. 회원 닉네임·가입 시각을 같이 쓰므로 지갑·회원을 함께 읽는다
	public Page<WalletCharge> findFlagged(Pageable pageable) {
		List<WalletCharge> charges = queryFactory.selectFrom(walletCharge)
			.join(walletCharge.wallet, wallet).fetchJoin()
			.join(wallet.holder, cashMember).fetchJoin()
			.where(walletCharge.reviewFlagged.isTrue())
			.orderBy(walletCharge.chargeDate.desc(), walletCharge.id.desc())
			.offset(pageable.getOffset())
			.limit(pageable.getPageSize())
			.fetch();
		Long total = queryFactory.select(walletCharge.count())
			.from(walletCharge)
			.where(walletCharge.reviewFlagged.isTrue())
			.fetchOne();
		return new PageImpl<>(charges, pageable, total == null ? 0 : total);
	}

	// 여러 지갑의 완료 충전 중 요청 시각이 [from, to]인 것. 회원마다 가입 24시간 창을 거르는 일은 부르는 쪽이 한다
	public List<WalletCharge> findCompletedCreatedBetween(Collection<Long> walletIds, LocalDateTime from,
		LocalDateTime to) {
		return queryFactory.selectFrom(walletCharge)
			.where(walletCharge.wallet.id.in(walletIds),
				walletCharge.status.eq(ChargeStatus.COMPLETED),
				walletCharge.createDate.between(from, to))
			.fetch();
	}
}
