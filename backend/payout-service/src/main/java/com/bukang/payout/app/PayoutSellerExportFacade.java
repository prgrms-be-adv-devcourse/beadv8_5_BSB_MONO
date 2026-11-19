package com.bukang.payout.app;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 판매자 정산 자료 CSV 내려받기(정산 내역·건별 정산)의 진입점
 * 회원 ID를 판매자 ID로 바꾼 뒤(승인된 판매자만) 그 판매자의 기록만 담는다
 *
 * 판매자 확인은 회원 서비스를 HTTP로 부르므로 트랜잭션 없이 실행하고, 조회·기록 UseCase가 자기 트랜잭션 하나를 연다
 * (PayoutSellerFacade와 같은 이유. 조회와 자료 내려받기 쓰임이 달라 Facade를 나눈다)
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PayoutSellerExportFacade {
	private final PayoutSellerVerifyUseCase sellerVerifyUseCase;
	private final PayoutSellerExportUseCase payoutSellerExportUseCase;

	public PayoutExportFile exportPayouts(long memberId, YearMonth fromMonth, YearMonth toMonth) {
		long sellerId = sellerVerifyUseCase.verify(memberId);
		return payoutSellerExportUseCase.exportPayouts(sellerId, memberId, fromMonth, toMonth);
	}

	public PayoutExportFile exportPayoutItems(long memberId, LocalDate from, LocalDate to) {
		long sellerId = sellerVerifyUseCase.verify(memberId);
		return payoutSellerExportUseCase.exportPayoutItems(sellerId, memberId, from, to);
	}
}
