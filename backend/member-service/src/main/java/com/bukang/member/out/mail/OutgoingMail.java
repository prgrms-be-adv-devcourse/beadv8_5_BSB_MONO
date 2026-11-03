package com.bukang.member.out.mail;

/**
 * 보낼 메일 한 통. 본문은 HTML과 일반 텍스트를 함께 넣어 받는 프로그램이 고르게 한다
 */
public record OutgoingMail(
	String to,
	String subject,
	String text,
	String html
) {
	// 받는 주소와 본문(재설정 링크)이 로그에 남지 않게 제목만 보인다
	@Override
	public String toString() {
		return "OutgoingMail[subject=" + subject + "]";
	}
}
