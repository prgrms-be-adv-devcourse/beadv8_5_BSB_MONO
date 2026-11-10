package com.bukang.payout.out;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 임시 어댑터: 캐시의 가상계좌 발급 API(BUK-84)가 아직 없어 발급하지 않는다
 * 계좌가 없는 청구는 지금처럼 목업 계좌를 알리고 관리자가 입금을 확인한다. BUK-84가 끝나면 진짜 어댑터로 바꾼다
 */
@Slf4j
@Component
public class NoClaimVirtualAccountAdapter implements ClaimVirtualAccountPort {
	@Override
	public Optional<ClaimVirtualAccount> issue(String claimCode, long amount, LocalDate dueDate) {
		log.warn("가상계좌 발급 연결 전이라 발급하지 않습니다. claimCode={}, amount={}", claimCode, amount);
		return Optional.empty();
	}
}
