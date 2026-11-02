package com.bukang.order.out.seller;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;

/**
 * 판매자 ID 조회를 아직 실제로 붙이지 않은 환경(운영)에서 판매자 API를 막는다 (PRO-197)
 * 가짜 어댑터는 누구나 판매자로 보므로 운영에서 켜면 안 된다. 실제 어댑터를 붙이면 mode를 바꾼다
 */
@Component
@ConditionalOnProperty(prefix = "order.clients.seller", name = "mode", havingValue = "disabled")
public class DisabledSellerLookupAdapter implements SellerLookupPort {
	static final String MESSAGE = "판매자 기능을 아직 쓸 수 없습니다. 잠시 뒤 다시 시도해주세요.";

	@Override
	public Optional<Long> findApprovedSellerId(long memberId) {
		throw new ExternalServiceUnavailableException(MESSAGE);
	}
}
