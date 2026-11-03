package com.bukang.member.out.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * prod인데 메일 설정(MAIL_HOST)이 비어 있을 때 쓴다. 링크가 로그에 남지 않도록 제목만 경고로 남긴다
 * 앱은 정상으로 뜨고 메일만 나가지 않는다
 */
@Slf4j
public class DisabledMemberMailSender implements MemberMailSender {
	@Override
	public void send(OutgoingMail mail) {
		log.warn("메일 설정(MAIL_HOST)이 없어 메일을 보내지 못했습니다: {}", mail);
	}
}
