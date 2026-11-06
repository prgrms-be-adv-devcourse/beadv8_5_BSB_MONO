package com.bukang.order.out.member;

import java.util.Collection;
import java.util.Map;

/**
 * 회원 ID 여러 개로 닉네임을 받는다 (파티 조회, BUK-39, 회원 내부 API #319)
 * 읽기만 하므로 몇 번 불러도 된다. 응답에 없는 회원은 결과에서 빠지고, 부르는 쪽이 「탈퇴한 회원」으로 보여 준다
 * 연결하지 못하면 ExternalServiceUnavailableException(503)을 던진다
 */
public interface MemberNicknamesPort {
	String WITHDRAWN_NICKNAME = "탈퇴한 회원";

	Map<Long, String> findNicknames(Collection<Long> memberIds);
}
