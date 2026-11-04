package com.bukang.member.out.mail;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

/**
 * spring.mail.* 설정(Gmail SMTP)으로 보낸다. 보내는 주소는 로그인한 Gmail 주소와 같아야 Gmail이 바꾸지 않는다
 */
@RequiredArgsConstructor
public class SmtpMemberMailSender implements MemberMailSender {
	private final JavaMailSender javaMailSender;
	private final String fromAddress;
	private final String fromName;

	@Override
	public void send(OutgoingMail mail) {
		MimeMessage message = javaMailSender.createMimeMessage();
		try {
			// multipart=true로 텍스트와 HTML을 함께 넣는다 (multipart/alternative)
			MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
			helper.setFrom(fromAddress, fromName);
			helper.setTo(mail.to());
			helper.setSubject(mail.subject());
			helper.setText(mail.text(), mail.html());
		} catch (MessagingException | UnsupportedEncodingException e) {
			throw new MailSendException("메일을 만들지 못했습니다: " + mail, e);
		}
		javaMailSender.send(message);
	}
}
