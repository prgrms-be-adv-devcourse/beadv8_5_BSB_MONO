package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.app.SocialJoinUseCase;
import com.bukang.member.domain.SocialProvider;
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.SocialJoinRequestDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.SocialAccountRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.mail.InMemoryMemberMailSender;
import com.bukang.member.out.mail.OutgoingMail;
import com.bukang.member.out.oauth2.InMemorySocialLoginStore;
import com.bukang.member.out.oauth2.PendingSignup;
import com.bukang.member.out.passwordreset.InMemoryPasswordResetStore;

// Redis와 메일 서버 없이 흐름을 확인한다. 재설정 저장소와 메일 발송기는 메모리 구현, 발송은 바로 실행한다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({InMemoryPasswordResetStore.Config.class, InMemoryMemberMailSender.Config.class,
	InMemoryIdentityVerificationStore.Config.class, InMemorySocialLoginStore.Config.class})
class ApiV1PasswordResetTest {
	private static final String BASE = "/api/v1/auth/password-reset/";
	private static final String EMAIL = "runner1@crewrun.com";
	private static final String PASSWORD = "password1234";
	private static final String NEW_PASSWORD = "newpassword1234";
	private static final int IP_DAILY_LIMIT = 20;
	private static final String LOGGED_OUT_SENTENCE = "다른 기기의 로그인도 모두 풀렸어요";
	private static final String LOG_OUT_YOURSELF_SENTENCE = "다른 기기에서 로그인되어 있다면 로그아웃해 주세요";
	private static final Pattern TOKEN = Pattern.compile("/password-reset/new#token=([A-Za-z0-9_-]+)");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonConverter jsonConverter;

	@Autowired
	private MemberFacade memberFacade;

	@Autowired
	private SocialJoinUseCase socialJoinUseCase;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private SocialAccountRepository socialAccountRepository;

	@Autowired
	private TermsAgreementRepository termsAgreementRepository;

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;

	@Autowired
	private InMemorySocialLoginStore socialLoginStore;

	@Autowired
	private InMemoryPasswordResetStore resetStore;

	@Autowired
	private InMemoryMemberMailSender mailSender;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	private long memberId;

	@BeforeEach
	void setUp() {
		memberId = memberFacade.join(identityStore.joinRequest(
			EMAIL, PASSWORD, "김러너", "러너1", "010-1234-5678", LocalDate.of(1995, 4, 12), null,
			List.of(TermsType.SERVICE, TermsType.PRIVACY)
		)).getId();
	}

	@AfterEach
	void tearDown() {
		resetStore.clear();
		mailSender.clear();
		socialLoginStore.clear();
		socialAccountRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("메일 링크로 비밀번호를 바꾸면 새 비밀번호로 로그인되고, 모든 기기 로그인이 풀리고, 변경 알림 메일이 간다")
	void resetPassword() throws Exception {
		requestReset(EMAIL).andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("가입된 이메일이면 재설정 메일을 보냈습니다."));

		OutgoingMail mail = mailSender.sent().getFirst();
		assertThat(mail.to()).isEqualTo(EMAIL);
		assertThat(mail.subject()).isEqualTo("[크루런] 비밀번호 재설정 링크");
		assertThat(mail.text()).contains("http://localhost:3000/password-reset/new#token=", "30분");
		assertThat(mail.html()).contains("http://localhost:3000/password-reset/new#token=");
		String token = tokenOf(mail);

		post(BASE + "verify", Map.of("token", token)).andExpect(status().isOk());
		post(BASE + "confirm", Map.of("token", token, "password", NEW_PASSWORD))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("비밀번호를 바꿨습니다."));

		login(PASSWORD).andExpect(status().isUnauthorized());
		login(NEW_PASSWORD).andExpect(status().isOk());
		verify(refreshTokenRepository).deleteAll(memberId);
		assertThat(mailSender.sent()).extracting(OutgoingMail::subject)
			.containsExactly("[크루런] 비밀번호 재설정 링크", "[크루런] 비밀번호가 바뀌었어요");
		assertThat(mailSender.sent().getLast().text()).contains(LOGGED_OUT_SENTENCE)
			.doesNotContain(LOG_OUT_YOURSELF_SENTENCE);
	}

	@Test
	@DisplayName("모든 기기 로그인 정리가 실패하면 알림 메일에 다 풀렸다고 쓰지 않고, 직접 로그아웃하라고 안내한다")
	void changedMailWhenLogoutCleanupFails() throws Exception {
		willThrow(new RedisConnectionFailureException("Redis 멈춤")).given(refreshTokenRepository).deleteAll(memberId);
		requestReset(EMAIL);
		String token = tokenOf(mailSender.sent().getFirst());

		post(BASE + "confirm", Map.of("token", token, "password", NEW_PASSWORD)).andExpect(status().isOk());

		OutgoingMail changed = mailSender.sent().getLast();
		assertThat(changed.subject()).isEqualTo("[크루런] 비밀번호가 바뀌었어요");
		assertThat(changed.text()).doesNotContain(LOGGED_OUT_SENTENCE).contains(LOG_OUT_YOURSELF_SENTENCE);
		assertThat(changed.html()).doesNotContain(LOGGED_OUT_SENTENCE).contains(LOG_OUT_YOURSELF_SENTENCE);
		login(NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	@DisplayName("같은 IP는 이메일을 바꿔도 하루 20번까지만 받고, 21번째는 429이며 메일을 보내지 않는다")
	void dailyLimitPerIp() throws Exception {
		for (int i = 0; i < IP_DAILY_LIMIT; i++) {
			requestReset("user" + i + "@crewrun.com", "203.0.113.7").andExpect(status().isOk());
		}

		requestReset(EMAIL, "203.0.113.7")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("AUTH_RESET_TOO_MANY"));
		assertThat(mailSender.sent()).isEmpty();
	}

	@Test
	@DisplayName("한 IP가 한도를 넘어도 다른 IP의 요청은 받는다")
	void otherIpStillAllowed() throws Exception {
		for (int i = 0; i < IP_DAILY_LIMIT; i++) {
			requestReset("user" + i + "@crewrun.com", "203.0.113.7").andExpect(status().isOk());
		}
		requestReset("over@crewrun.com", "203.0.113.7").andExpect(status().isTooManyRequests());

		requestReset(EMAIL, "198.51.100.9").andExpect(status().isOk());
		assertThat(mailSender.sent()).extracting(OutgoingMail::to).containsExactly(EMAIL);
	}

	@Test
	@DisplayName("X-Forwarded-For는 무시하고 X-Real-IP로 세므로 X-Forwarded-For를 바꿔도 한도를 피할 수 없다")
	void forgedForwardedForCannotBypass() throws Exception {
		for (int i = 0; i < IP_DAILY_LIMIT; i++) {
			requestReset("user" + i + "@crewrun.com", "203.0.113.7", "10.0.0." + i).andExpect(status().isOk());
		}

		requestReset(EMAIL, "203.0.113.7", "10.0.0.99").andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName("IP 한도에 걸린 요청은 그 이메일의 횟수를 쓰지 않아, 다른 IP에서 같은 이메일로 바로 요청할 수 있다")
	void blockedIpDoesNotSpendEmailQuota() throws Exception {
		for (int i = 0; i < IP_DAILY_LIMIT; i++) {
			requestReset("user" + i + "@crewrun.com", "203.0.113.7").andExpect(status().isOk());
		}
		for (int i = 0; i < 5; i++) {
			requestReset(EMAIL, "203.0.113.7").andExpect(status().isTooManyRequests());
		}

		requestReset(EMAIL, "198.51.100.9").andExpect(status().isOk());
		assertThat(mailSender.sent()).extracting(OutgoingMail::to).containsExactly(EMAIL);
	}

	@Test
	@DisplayName("X-Real-IP가 없으면(Nginx 없는 로컬) 접속 주소로 세어 한도를 건다")
	void fallsBackToRemoteAddress() throws Exception {
		for (int i = 0; i < IP_DAILY_LIMIT; i++) {
			requestReset("user" + i + "@crewrun.com").andExpect(status().isOk());
		}

		requestReset(EMAIL).andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName("같은 링크로 두 번 바꾸려 하면 두 번째는 쓸 수 없는 링크로 거절한다")
	void tokenIsSingleUse() throws Exception {
		requestReset(EMAIL);
		String token = tokenOf(mailSender.sent().getFirst());
		post(BASE + "confirm", Map.of("token", token, "password", NEW_PASSWORD)).andExpect(status().isOk());

		post(BASE + "confirm", Map.of("token", token, "password", "another12345"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_RESET_TOKEN_INVALID"));
		post(BASE + "verify", Map.of("token", token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_RESET_TOKEN_INVALID"));
	}

	@Test
	@DisplayName("새 링크를 요청하면 이전 링크는 쓸 수 없다")
	void newLinkInvalidatesPrevious() throws Exception {
		requestReset(EMAIL);
		String first = tokenOf(mailSender.sent().getFirst());
		resetStore.clearCooldowns();
		requestReset(EMAIL);
		String second = tokenOf(mailSender.sent().getLast());

		post(BASE + "verify", Map.of("token", first)).andExpect(status().isBadRequest());
		post(BASE + "verify", Map.of("token", second)).andExpect(status().isOk());
	}

	@Test
	@DisplayName("비밀번호를 바꾸는 중에 새 링크를 요청해도, 그다음 새 링크를 받으면 그 사이 링크는 쓸 수 없다")
	void linkRequestedDuringConfirmIsInvalidatedByNextLink() throws Exception {
		requestReset(EMAIL);
		String first = tokenOf(mailSender.sent().getFirst());
		// 변경이 커밋되기 전에 같은 회원의 새 링크 요청이 끼어든 상황
		new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
			try {
				post(BASE + "confirm", Map.of("token", first, "password", NEW_PASSWORD)).andExpect(status().isOk());
				resetStore.clearCooldowns();
				requestReset(EMAIL).andExpect(status().isOk());
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});
		String during = tokenOf(mailSender.sent().stream()
			.filter(mail -> TOKEN.matcher(mail.text()).find()).toList().getLast());
		resetStore.clearCooldowns();
		requestReset(EMAIL);
		String latest = tokenOf(mailSender.sent().getLast());

		post(BASE + "verify", Map.of("token", during)).andExpect(status().isBadRequest());
		post(BASE + "verify", Map.of("token", latest)).andExpect(status().isOk());
	}

	@Test
	@DisplayName("가입하지 않은 이메일도 같은 응답을 받고, 메일은 가지 않는다")
	void unknownEmailGetsSameResponse() throws Exception {
		String known = requestReset(EMAIL).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String unknown = requestReset("nobody@crewrun.com").andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(unknown).isEqualTo(known);
		assertThat(mailSender.sent()).extracting(OutgoingMail::to).containsExactly(EMAIL);
	}

	@Test
	@DisplayName("1분 안에 같은 이메일로 다시 요청하면 429다. 가입하지 않은 이메일도 같다")
	void tooManyRequests() throws Exception {
		requestReset(EMAIL);
		requestReset(EMAIL).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("AUTH_RESET_TOO_MANY"));

		requestReset("nobody@crewrun.com");
		requestReset("nobody@crewrun.com").andExpect(status().isTooManyRequests());
		assertThat(mailSender.sent()).hasSize(1);
	}

	@Test
	@DisplayName("대소문자만 다른 이메일도 같은 이메일로 세어 제한한다")
	void limitIgnoresCase() throws Exception {
		requestReset(EMAIL);

		requestReset("Runner1@CrewRun.com").andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName("하루 5번을 넘기면 1분이 지나도 429다")
	void dailyLimit() throws Exception {
		for (int i = 0; i < 5; i++) {
			resetStore.clearCooldowns();
			requestReset(EMAIL).andExpect(status().isOk());
		}
		resetStore.clearCooldowns();

		requestReset(EMAIL).andExpect(status().isTooManyRequests());
		assertThat(mailSender.sent()).hasSize(5);
	}

	@Test
	@DisplayName("비밀번호 없는 소셜 가입 회원에게는 링크 대신 소셜 로그인 안내 메일을 보낸다")
	void socialMemberGetsGuidance() throws Exception {
		String socialEmail = "social@kakao.com";
		socialLoginStore.savePendingSignup("pending-1",
			new PendingSignup(SocialProvider.KAKAO, "kakao-1", socialEmail, null));
		String identityToken = identityStore.verify("김소셜", LocalDate.of(1995, 4, 12), "010-9876-5432");
		socialJoinUseCase.join("pending-1", new SocialJoinRequestDto(null, "소셜러너", null,
			List.of(TermsType.SERVICE, TermsType.PRIVACY), identityToken));

		requestReset(socialEmail).andExpect(status().isOk());

		OutgoingMail mail = mailSender.sent().getFirst();
		assertThat(mail.subject()).isEqualTo("[크루런] 로그인 방법 안내");
		assertThat(mail.text()).contains("카카오로 가입한 계정", "http://localhost:3000/login")
			.doesNotContain("#token=");
	}

	@Test
	@DisplayName("링크 메일 발송이 실패하면 다시 보낸다")
	void retriesFailedMail() throws Exception {
		mailSender.failNext(2);

		requestReset(EMAIL).andExpect(status().isOk());

		assertThat(mailSender.attempts()).isEqualTo(3);
		assertThat(mailSender.sent()).hasSize(1);
	}

	@Test
	@DisplayName("새 비밀번호가 10자보다 짧으면 400이고 링크는 그대로 쓸 수 있다")
	void shortPasswordRejected() throws Exception {
		requestReset(EMAIL);
		String token = tokenOf(mailSender.sent().getFirst());

		post(BASE + "confirm", Map.of("token", token, "password", "short"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		post(BASE + "verify", Map.of("token", token)).andExpect(status().isOk());
		verify(refreshTokenRepository, never()).deleteAll(anyLong());
	}

	@Test
	@DisplayName("모르는 토큰은 쓸 수 없는 링크로 거절한다")
	void unknownToken() throws Exception {
		post(BASE + "confirm", Map.of("token", "unknown-token", "password", NEW_PASSWORD))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_RESET_TOKEN_INVALID"));
	}

	private ResultActions requestReset(String email) throws Exception {
		return post(BASE + "request", Map.of("email", email));
	}

	// Nginx가 접속자 IP로 덮어써 넣는 X-Real-IP를 흉내 낸다
	private ResultActions requestReset(String email, String realIp) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post(BASE + "request")
			.header("X-Real-IP", realIp)
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonConverter.toJson(Map.of("email", email))));
	}

	// 클라이언트가 X-Forwarded-For를 위조해 보낸 경우
	private ResultActions requestReset(String email, String realIp, String forwardedFor) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post(BASE + "request")
			.header("X-Real-IP", realIp)
			.header("X-Forwarded-For", forwardedFor)
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonConverter.toJson(Map.of("email", email))));
	}

	private ResultActions login(String password) throws Exception {
		return post("/api/v1/auth/login", Map.of("email", EMAIL, "password", password));
	}

	private ResultActions post(String url, Map<String, String> body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post(url)
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonConverter.toJson(body)));
	}

	private static String tokenOf(OutgoingMail mail) {
		Matcher matcher = TOKEN.matcher(mail.text());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}
}
