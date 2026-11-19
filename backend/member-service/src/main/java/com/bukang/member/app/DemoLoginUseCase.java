package com.bukang.member.app;

import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.config.auth.DemoLoginProperties;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.exception.DemoLoginTooManyRequestsException;
import com.bukang.member.domain.exception.MemberNotFoundException;
import com.bukang.member.dto.MemberDto;
import com.bukang.member.dto.MemberLoginResult;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.demologin.DemoLoginStore;

import lombok.RequiredArgsConstructor;

/**
 * 발표 시연용 데모 로그인. 설정(member.auth.demo-login.email)에 적힌 계정 하나만 비밀번호 없이 로그인시킨다
 * 토큰은 이메일 로그인과 같은 TokenIssueUseCase로 발급해 Refresh Token 회전·재사용 탐지를 그대로 받는다
 */
@Service
@RequiredArgsConstructor
public class DemoLoginUseCase {
	static final String TOO_MANY_MESSAGE = "요청이 너무 많습니다. 잠시 뒤 다시 시도해주세요.";
	// 같은 IP의 반복 호출을 막는다. Refresh Token 기록은 회원당 하나라 누가 로그인할 때마다 이전 로그인이 끊긴다
	// Redis 오류는 그대로 올려 실패시킨다. 횟수를 못 세는 동안 관리자 계정 로그인을 열어 두지 않는다
	private static final int LIMIT = 10;
	private static final Duration WINDOW = Duration.ofMinutes(1);

	private final DemoLoginProperties properties;
	private final DemoLoginStore store;
	private final MemberRepository memberRepository;
	private final TokenIssueUseCase tokenIssueUseCase;

	// 이메일 로그인(MemberFacade.login)과 같다. 트랜잭션 없이 실행하되, 같은 범위에서 엔티티 매니저를 이어 써서
	// 회원 정보를 응답 모양으로 바꿀 때 소셜 연결 목록을 읽을 수 있다. 토큰 발급은 Redis만 쓴다
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public MemberLoginResult login(String clientIp) {
		if (!store.tryAcquire(Sha256.hex(clientIp), LIMIT, WINDOW)) {
			throw new DemoLoginTooManyRequestsException(TOO_MANY_MESSAGE);
		}
		Member member = memberRepository.findActiveByEmail(properties.email())
			.orElseThrow(() -> new MemberNotFoundException("데모 로그인 계정이 없습니다."));
		return new MemberLoginResult(MemberDto.from(member), tokenIssueUseCase.issue(member.getId()));
	}
}
