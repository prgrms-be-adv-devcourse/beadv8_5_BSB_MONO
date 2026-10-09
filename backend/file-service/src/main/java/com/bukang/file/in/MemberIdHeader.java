package com.bukang.file.in;

/**
 * 요청한 회원 ID를 담는 헤더.
 * 게이트웨이가 토큰을 검사해 넣어 줄 예정이다(PRO-20). 헤더 이름이 정해지면 여기만 바꾼다.
 */
public final class MemberIdHeader {
	public static final String NAME = "X-Member-Id";

	private MemberIdHeader() {
	}
}
