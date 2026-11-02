package com.bukang.order.out.member;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;

/**
 * 닉네임 조회를 아직 실제로 붙이지 않은 환경(운영)에서 닉네임 검색을 막는다 (BUK-26)
 * 가짜 어댑터는 시작 데이터 닉네임만 알아 운영 회원을 잘못 찾을 수 있다. 회원 서비스 API가 생기면 HTTP 어댑터로 바꾼다
 * 주문번호 검색은 회원 서비스가 필요 없어 그대로 쓸 수 있다
 */
@Component
@ConditionalOnProperty(prefix = "order.clients.nickname", name = "mode", havingValue = "disabled")
public class DisabledMemberNicknameAdapter implements MemberNicknamePort {
	static final String MESSAGE = "닉네임 검색은 아직 쓸 수 없습니다. 주문번호로 찾아 주세요.";

	@Override
	public Optional<Long> findMemberId(String nickname) {
		throw new ExternalServiceUnavailableException(MESSAGE);
	}
}
