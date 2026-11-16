package com.bukang.order.out.seller;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;

/**
 * 회원 서비스를 부를 수 없는 환경에서 판매자 API를 막는다 (PRO-197). 운영은 실제 어댑터(mode=http, BUK-237)를 쓴다
 * 가짜 어댑터는 누구나 판매자로 보므로 운영에서 켜면 안 된다
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
