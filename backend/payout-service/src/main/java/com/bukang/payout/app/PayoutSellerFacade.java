package com.bukang.payout.app;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.dto.PageDto;
import com.bukang.payout.dto.PayoutDto;
import com.bukang.payout.dto.PayoutItemDto;
import com.bukang.payout.dto.PayoutSummaryDto;
import com.bukang.payout.dto.SellerPayoutHoldDto;

import lombok.RequiredArgsConstructor;

/**
 * 판매자 정산 조회 API(요약, 정산 내역, 건별 정산, 지급보류 내역)의 진입점.
 * 회원 ID를 판매자 ID로 바꾼 뒤(승인된 판매자만) 그 판매자의 기록만 조회한다
 *
 * 판매자 확인은 회원 서비스를 HTTP로 부르므로, 응답을 기다리는 동안 DB 연결을 잡지 않게 트랜잭션 없이 실행하고
 * 조회 UseCase가 자기 트랜잭션을 연다. 그래서 메서드마다 트랜잭션 UseCase는 하나만 부른다 (둘을 부르면 따로 커밋된다)
 * NOT_SUPPORTED는 바깥 트랜잭션을 멈출 뿐 그 DB 연결을 놓지는 않는다
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PayoutSellerFacade {
	private final PayoutSellerVerifyUseCase sellerVerifyUseCase;
	private final PayoutSellerQueryUseCase payoutSellerQueryUseCase;

	public PayoutSummaryDto summary(long memberId, LocalDate today) {
		long sellerId = sellerVerifyUseCase.verify(memberId);
		return payoutSellerQueryUseCase.summary(sellerId, today);
	}

	public List<PayoutDto> payouts(long memberId, YearMonth fromMonth, YearMonth toMonth) {
		long sellerId = sellerVerifyUseCase.verify(memberId);
		return payoutSellerQueryUseCase.payouts(sellerId, fromMonth, toMonth);
	}

	public PageDto<PayoutItemDto> items(long memberId, LocalDate from, LocalDate to, int page, int size) {
		long sellerId = sellerVerifyUseCase.verify(memberId);
		return payoutSellerQueryUseCase.items(sellerId, from, to, page, size);
	}

	public List<SellerPayoutHoldDto> holds(long memberId, LocalDate from, LocalDate to) {
		long sellerId = sellerVerifyUseCase.verify(memberId);
		return payoutSellerQueryUseCase.holds(sellerId, from, to);
	}
}
