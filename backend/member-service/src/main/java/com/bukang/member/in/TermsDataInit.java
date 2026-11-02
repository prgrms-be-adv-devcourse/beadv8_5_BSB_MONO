package com.bukang.member.in;

import java.time.LocalDate;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import com.bukang.member.domain.Terms;
import com.bukang.member.domain.TermsType;
import com.bukang.member.out.TermsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기본 약관 데이터 (BE-021)
 * 가입에 필요한 약관이라 모든 프로파일에서 실행하고, 이미 있으면 만들지 않는다.
 * 기본 회원을 만드는 MemberDataInit(@Order(1))보다 먼저 실행한다
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class TermsDataInit {
	private static final String BASE_VERSION = "1.1";
	// 웹 약관 화면(1.1, 2026.10.30)과 같은 날짜다. 이 날짜부터 가입하는 회원은 1.1에 동의한 것으로 저장된다
	// 이미 만들어진 1.0 약관은 지우지 않아서, 그 전에 가입한 회원의 동의 기록이 그대로 남는다
	private static final LocalDate BASE_EFFECTIVE_DATE = LocalDate.of(2026, 10, 30);

	private final TermsRepository termsRepository;

	@Bean
	@Order(0)
	public ApplicationRunner termsDataInitApplicationRunner() {
		return args -> makeBaseTerms();
	}

	public void makeBaseTerms() {
		make(TermsType.SERVICE, true, "/terms/service");
		make(TermsType.PRIVACY, true, "/terms/privacy");
		make(TermsType.MARKETING, false, "/terms/marketing");
	}

	private void make(TermsType type, boolean required, String contentUrl) {
		if (termsRepository.existsByTypeAndVersion(type, BASE_VERSION)) {
			return;
		}

		termsRepository.save(new Terms(type, BASE_VERSION, required, contentUrl, BASE_EFFECTIVE_DATE));
		log.info("기본 약관 생성: {} {}", type, BASE_VERSION);
	}
}
