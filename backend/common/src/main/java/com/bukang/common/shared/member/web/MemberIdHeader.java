package com.bukang.common.shared.member.web;

/**
 * 게이트웨이가 Access Token을 검사한 뒤 넘겨주는 회원 ID 헤더 (PRO-20).
 * 컨트롤러는 이 헤더를 직접 읽지 않고 @LoginMemberId로 받는다. 읽는 규칙은 LoginMemberIdArgumentResolver에 있다.
 * gateway는 common에 의존하지 않아 JwtAuthenticationFilter에 같은 헤더 이름을 따로 둔다
 */
public final class MemberIdHeader {
	public static final String NAME = "X-Member-Id";
	public static final String INVALID_MESSAGE = "회원 ID가 올바르지 않습니다.";

	private MemberIdHeader() {
	}
}
