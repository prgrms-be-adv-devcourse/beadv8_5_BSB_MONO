package com.bukang.member.out.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * 메일 설정이 없는 dev·test에서 쓴다. 메일 대신 로그에 텍스트 본문(재설정 링크 포함)을 찍어 링크를 복사해 쓰게 한다
 * 로그를 볼 수 있는 사람이 남의 비밀번호를 바꿀 수 있으므로 prod에서는 쓰지 않는다 (MemberMailConfig)
 */
@Slf4j
public class LoggingMemberMailSender implements MemberMailSender {
	@Override
	public void send(OutgoingMail mail) {
		log.info("메일 설정이 없어 보내지 않고 로그로 남깁니다. 받는 주소: {}, 제목: {}\n{}", mail.to(), mail.subject(), mail.text());
	}
}
