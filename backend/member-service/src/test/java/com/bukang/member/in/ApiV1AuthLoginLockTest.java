package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.Member;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.loginattempt.InMemoryLoginAttemptStore;
import com.bukang.member.support.MemberFixture;

// 5번 실패하면 막는 규칙을 API로 확인한다 (BUK-185). 시간이 지나면 풀리는지는 RedisLoginAttemptStoreTest가 본다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1AuthLoginLockTest {
	private static final String EMAIL = "runner1@crewrun.com";
	private static final String PASSWORD = "password1234";
	private static final String LOCKED_MESSAGE =
		"로그인을 5번 실패해 10분 동안 로그인할 수 없습니다. 잠시 뒤 다시 시도해주세요.";

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;

	@MockitoSpyBean
	private InMemoryLoginAttemptStore loginAttemptStore;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberFacade memberFacade;

	@MockitoSpyBean
	private MemberRepository memberRepository;

	@Autowired
	private TermsAgreementRepository termsAgreementRepository;

	@MockitoSpyBean
	private AuthenticationManager authenticationManager;

	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	@BeforeEach
	void setUp() {
		memberFacade.join(MemberFixture.joinRequest().email(EMAIL).password(PASSWORD).build(identityStore));
	}

	@AfterEach
	void tearDown() {
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("5번 실패하면 6번째부터는 비밀번호가 맞아도 비교 없이 429 AUTH_LOGIN_LOCKED로 거절한다")
	void sixthAttemptIsLockedEvenWithCorrectPassword() throws Exception {
		failTimes(EMAIL, 5);
		clearInvocations(authenticationManager);

		login(EMAIL, PASSWORD)
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("AUTH_LOGIN_LOCKED"))
			.andExpect(jsonPath("$.message").value(LOCKED_MESSAGE));
		verify(authenticationManager, never()).authenticate(any());
		verify(refreshTokenRepository, never()).create(anyLong(), any());
	}

	@Test
	@DisplayName("5번째 실패까지는 401이다")
	void fifthFailureIsStillUnauthorized() throws Exception {
		failTimes(EMAIL, 5);
	}

	@Test
	@DisplayName("5번 안에 로그인에 성공하면 횟수를 지워, 처음부터 다시 센다")
	void successResetsCount() throws Exception {
		failTimes(EMAIL, 4);
		login(EMAIL, PASSWORD).andExpect(status().isOk());

		failTimes(EMAIL, 5);
	}

	@Test
	@DisplayName("가입하지 않은 이메일도 똑같이 세고 막는다")
	void unknownEmailIsCountedToo() throws Exception {
		failTimes("nobody@crewrun.com", 5);

		login("nobody@crewrun.com", PASSWORD).andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName("대소문자와 앞뒤 공백이 달라도 같은 이메일로 세고, 다른 이메일에는 영향이 없다")
	void sameEmailRegardlessOfCaseAndSpaces() throws Exception {
		failTimes(" Runner1@CrewRun.com ", 3);
		failTimes("RUNNER1@crewrun.com", 2);

		login(EMAIL, PASSWORD).andExpect(status().isTooManyRequests());
		memberFacade.join(MemberFixture.joinRequest()
			.email("other@crewrun.com").password(PASSWORD).build(identityStore));
		login("other@crewrun.com", PASSWORD).andExpect(status().isOk());
	}

	@Test
	@DisplayName("Redis에는 이메일 원문이 아니라 해시만 남는다")
	void storesOnlyEmailHash() throws Exception {
		failTimes(EMAIL, 1);

		assertThat(loginAttemptStore.counts().keySet())
			.hasSize(1)
			.allSatisfy(key -> assertThat(key).matches("[0-9a-f]{64}"));
	}

	@Test
	@DisplayName("DB가 같은 회원으로 찾아 주는 다른 표기(악센트)로 시도해도 저장된 이메일 기준으로 센다")
	void countsByStoredEmailWhenLookupMatchesDifferentSpelling() throws Exception {
		String accented = "r\u00fanner1@crewrun.com";
		Member stored = memberRepository.findActiveByEmail(EMAIL).orElseThrow();
		doReturn(Optional.of(stored)).when(memberRepository).findActiveByEmail(accented);

		failTimes(accented, 5);

		assertThat(loginAttemptStore.counts().keySet()).containsExactly(sha256(EMAIL));
		login(EMAIL, PASSWORD).andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName("횟수를 올릴 때 Redis 오류가 나면 차단 없이 로그인을 계속한다")
	void continuesWhenRecordFails() throws Exception {
		doThrow(new RedisConnectionFailureException("Redis 연결 실패"))
			.when(loginAttemptStore).record(anyString(), anyInt(), any());

		login(EMAIL, PASSWORD).andExpect(status().isOk());
		login(EMAIL, "wrong-password").andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("횟수를 지울 때 Redis 오류가 나도 로그인은 성공한다")
	void continuesWhenClearFails() throws Exception {
		doThrow(new RedisConnectionFailureException("Redis 연결 실패"))
			.when(loginAttemptStore).clear(anyString());

		login(EMAIL, PASSWORD).andExpect(status().isOk());
	}

	private static String sha256(String value) throws Exception {
		return HexFormat.of().formatHex(
			MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
	}

	private void failTimes(String email, int times) throws Exception {
		for (int i = 0; i < times; i++) {
			login(email, "wrong-password").andExpect(status().isUnauthorized());
		}
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "email": "%s", "password": "%s" }
				""".formatted(email, password)));
	}
}
