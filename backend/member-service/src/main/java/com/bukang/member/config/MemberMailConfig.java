package com.bukang.member.config;

import java.util.Arrays;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

import com.bukang.member.config.auth.PasswordResetProperties;
import com.bukang.member.out.mail.DisabledMemberMailSender;
import com.bukang.member.out.mail.ExecutorMailTaskScheduler;
import com.bukang.member.out.mail.LoggingMemberMailSender;
import com.bukang.member.out.mail.MailTaskScheduler;
import com.bukang.member.out.mail.MemberMailSender;
import com.bukang.member.out.mail.SmtpMemberMailSender;

import lombok.extern.slf4j.Slf4j;

/**
 * 회원 메일 발송기를 고른다 (BUK-40)
 * spring.mail.host가 있으면 SMTP, 없으면 dev·test는 로그 출력, prod는 경고만 남기는 발송기다
 * host가 빈 문자열이어도 Spring Boot가 JavaMailSender를 만들 수 있어서, 빈 존재 대신 host 값으로 고른다
 */
@Slf4j
@Configuration
public class MemberMailConfig {
	private static final String PROD_PROFILE = "prod";

	@Bean
	public MemberMailSender memberMailSender(
		Environment environment,
		ObjectProvider<JavaMailSender> javaMailSender,
		PasswordResetProperties properties
	) {
		boolean prod = Arrays.asList(environment.getActiveProfiles()).contains(PROD_PROFILE);
		if (prod && !StringUtils.hasText(properties.frontBaseUrl())) {
			log.warn("FRONT_PUBLIC_BASE_URL이 없어 비밀번호 재설정 메일의 링크가 열리지 않습니다");
		}

		String host = environment.getProperty("spring.mail.host");
		JavaMailSender sender = javaMailSender.getIfAvailable();
		if (StringUtils.hasText(host) && sender != null) {
			return new SmtpMemberMailSender(sender, environment.getProperty("spring.mail.username"),
				properties.fromName());
		}
		if (prod) {
			log.warn("메일 설정(MAIL_HOST)이 없어 비밀번호 재설정 메일을 보내지 않습니다");
			return new DisabledMemberMailSender();
		}
		return new LoggingMemberMailSender();
	}

	@Bean
	public MailTaskScheduler mailTaskScheduler() {
		return new ExecutorMailTaskScheduler();
	}
}
