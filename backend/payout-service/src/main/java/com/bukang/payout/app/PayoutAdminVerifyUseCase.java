package com.bukang.payout.app;

import org.springframework.stereotype.Service;

import com.bukang.payout.domain.exception.PayoutNotAdminException;
import com.bukang.payout.out.MemberAdminPort;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 API(지급보류 걸기·풀기, 회수 완료)를 부른 회원이 관리자인지 확인한다 (PRO-265)
 * 게이트웨이는 토큰만 확인하고 역할은 보지 않으므로 정산이 직접 막는다. 판정은 회원 서비스 DB의 역할 칸으로 한다
 *
 * PayoutAdminFacade가 UseCase보다 먼저 부른다. 일부러 트랜잭션을 걸지 않는다:
 * UseCase 안(@Transactional)에서 부르면 회원 서비스 응답을 기다리는 동안(최대 8초) DB 연결을 잡고 있게 된다
 */
@Service
@RequiredArgsConstructor
public class PayoutAdminVerifyUseCase {
	private final MemberAdminPort memberAdminPort;

	public void verify(long memberId) {
		if (!memberAdminPort.isAdmin(memberId)) {
			throw new PayoutNotAdminException();
		}
	}
}
