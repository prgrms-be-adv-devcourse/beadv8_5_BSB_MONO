package com.bukang.order.out.member;

import java.util.Optional;

/**
 * 회원 닉네임으로 회원 ID를 찾는다 (관리자 회원별 주문 조회, BUK-26)
 * 주문에는 구매자 ID만 있고 닉네임 원본은 회원 서비스에 있다. 회원 서비스에 아직 닉네임 조회 내부 API가 없어
 * 실제 연결은 그 API가 생긴 뒤 HTTP 어댑터만 붙인다 (예: GET /internal/v1/member/members?nickname=)
 * 닉네임은 회원 서비스에서 유일하다(uk_member_nickname). 읽기만 하므로 몇 번 불러도 된다
 * 연결하지 못하면 빈 값이 아니라 ExternalServiceUnavailableException(503)을 던진다
 */
public interface MemberNicknamePort {
	/**
	 * @return 그 닉네임의 회원 ID. 없거나 탈퇴한 회원이면 빈 값
	 */
	Optional<Long> findMemberId(String nickname);
}
