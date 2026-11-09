package com.bukang.payout.out;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 청구 전용 가상계좌를 발급받는 계약 (포트, BUK-88)
 * 발급은 캐시 서비스가 토스 가상계좌 API로 한다 (BUK-84). 캐시의 내부 API가 생기면 이 인터페이스를 구현한 어댑터를 붙인다
 */
@FunctionalInterface
public interface ClaimVirtualAccountPort {
	/**
	 * @param claimCode 청구 ID. 캐시가 토스 orderId로 써서 입금이 어느 청구 건인지 맞춘다
	 * @param amount 판매자가 입금할 금액 (청구 금액)
	 * @param dueDate 입금 기한
	 * @return 발급한 계좌. 아직 발급할 수 없으면 빈 값이고, 다음 청구 배치에서 다시 요청한다
	 */
	Optional<ClaimVirtualAccount> issue(String claimCode, long amount, LocalDate dueDate);
}
