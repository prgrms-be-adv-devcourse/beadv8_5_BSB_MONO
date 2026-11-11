package com.bukang.cash.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.app.charge.CashChargeReviewQueryUseCase;
import com.bukang.cash.domain.exception.admin.CashAdminAccessDeniedException;
import com.bukang.cash.dto.PageDto;
import com.bukang.cash.dto.charge.ChargeReviewDto;
import com.bukang.cash.out.member.MemberAdminPort;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 API의 입구. 관리자 확인이 회원 서비스를 부르므로 바깥 트랜잭션 없이 먼저 끝내고,
 * 조회 트랜잭션은 UseCase가 연다 (회원 서비스를 기다리는 동안 DB 연결을 잡지 않는다)
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RequiredArgsConstructor
public class AdminCashFacade {
	private final MemberAdminPort memberAdminPort;
	private final CashChargeReviewQueryUseCase cashChargeReviewQueryUseCase;

	public PageDto<ChargeReviewDto> findChargeReviews(long adminId, int page, int size) {
		if (!memberAdminPort.isAdmin(adminId)) {
			throw new CashAdminAccessDeniedException();
		}
		return cashChargeReviewQueryUseCase.findFlagged(page, size);
	}
}
