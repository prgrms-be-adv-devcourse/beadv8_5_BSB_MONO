package com.bukang.ai.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 챗봇이 놓인 화면. 화면마다 필요한 역할과 대화 기록이 따로다
 */
@Getter
@RequiredArgsConstructor
public enum AiScreen {
	SELLER("판매자 센터"),
	ADMIN("관리자 센터");

	private final String label;

	/**
	 * 대화 번호. 화면이 보낸 값이 아니라 로그인한 회원 번호로 서버가 만든다 (남의 대화를 볼 수 없게)
	 * Redis 키는 ai:chats:{화면}-{회원 번호}:{순번}이 된다 (앞부분은 application.yaml의 key-prefix)
	 * 화면과 회원 번호 사이에 콜론을 쓰지 않는다. Spring AI가 대화 번호 속 콜론을 \\:로 바꿔 키에 넣는다
	 */
	public String conversationId(long memberId) {
		return name().toLowerCase() + "-" + memberId;
	}
}
