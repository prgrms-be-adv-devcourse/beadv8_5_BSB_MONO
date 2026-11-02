package com.bukang.member.out.mail;

/**
 * 회원 메일을 실제로 보낸다 (BUK-40). 운영은 Gmail SMTP, 메일 설정이 없으면 로그 출력 또는 경고, 테스트는 메모리 구현이다
 * 실패하면 예외를 던진다. 다시 보내기는 MailDispatcher가 한다
 */
public interface MemberMailSender {
	void send(OutgoingMail mail);
}
