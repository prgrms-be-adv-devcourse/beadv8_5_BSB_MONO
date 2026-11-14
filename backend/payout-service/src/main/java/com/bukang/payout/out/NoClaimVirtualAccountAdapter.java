package com.bukang.payout.out;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 테스트용 어댑터: 캐시 서비스 없이 뜨도록 발급하지 않는다. 그 밖의 프로파일은 CashClaimVirtualAccountAdapter가 뜬다
 * 계좌가 없는 청구는 목업 계좌를 알리고 관리자가 입금을 확인한다
 */
@Slf4j
@Component
@Profile("test")
public class NoClaimVirtualAccountAdapter implements ClaimVirtualAccountPort {
	@Override
	public Optional<ClaimVirtualAccount> issue(String claimCode, long amount, LocalDate dueDate) {
		log.warn("가상계좌 발급 연결 전이라 발급하지 않습니다. claimCode={}, amount={}", claimCode, amount);
		return Optional.empty();
	}
}
