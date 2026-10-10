package com.bukang.member.in;

import java.time.LocalDate;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.app.MemberFacade;
import com.bukang.member.dto.MemberJoinRequestDto;

import lombok.extern.slf4j.Slf4j;

/**
 * 개발용 기본 회원 데이터
 * 알려진 비밀번호를 쓰므로 dev 프로파일에서만 실행한다 (prod, test에서는 생성하지 않음)
 */
@Profile("dev")
@Configuration
@Slf4j
public class MemberDataInit {
	private static final String BASE_PASSWORD = "12345678910";
	private static final LocalDate BASE_BIRTH_DATE = LocalDate.of(1995, 1, 1);

	private final MemberDataInit self;
	private final MemberFacade memberFacade;

	public MemberDataInit(
		@Lazy MemberDataInit self,
		MemberFacade memberFacade
	) {
		this.self = self;
		this.memberFacade = memberFacade;
	}

	@Bean
	@Order(1)
	public ApplicationRunner memberDataInitApplicationRunner() {
		return args -> self.makeBaseMembers();
	}

	@Transactional
	public void makeBaseMembers() {
		// dev DB는 재시작해도 데이터가 남으므로(ddl-auto: update) 이미 만든 회원은 건너뛴다
		join("시스템", "system", "system@crewrun.com", "010-1111-2222");
		join("보관", "holding", "holding@crewrun.com", "010-1111-2223");
		join("관리자", "관리자", "admin@crewrun.com", "010-1111-2224");
		join("사용자1", "사용자1", "user1@crewrun.com", "010-1111-2225");
		join("사용자2", "사용자2", "user2@crewrun.com", "010-1111-2226");
		join("사용자3", "사용자3", "user3@crewrun.com", "010-1111-2227");
	}

	private void join(String name, String nickname, String email, String phone) {
		if (memberFacade.existsByEmail(email)) {
			return;
		}

		memberFacade.join(new MemberJoinRequestDto(
			email, BASE_PASSWORD, name, nickname, phone, BASE_BIRTH_DATE, null
		));
		log.info("기본 회원 생성: {}", email);
	}
}
