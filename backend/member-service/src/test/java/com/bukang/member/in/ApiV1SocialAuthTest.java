package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.SocialProvider;
import com.bukang.member.domain.TermsType;
import com.bukang.member.domain.exception.SocialLoginFailedException;
import com.bukang.member.dto.MemberJoinRequestDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.RefreshTokenRepository.NewFamily;
import com.bukang.member.out.SocialAccountRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.oauth2.InMemorySocialLoginStore;
import com.bukang.member.out.oauth2.SocialIdentity;
import com.bukang.member.out.oauth2.SocialOAuth2Client;
import com.bukang.member.out.oauth2.SocialOAuth2Client.AuthorizationStart;
import com.bukang.member.out.oauth2.StoredAuthorization;

import jakarta.servlet.http.Cookie;

// 카카오·구글 키 없이 흐름을 확인한다. 제공자와 주고받는 부분(SocialOAuth2Client)은 목으로, Redis 상태 저장소는 메모리 구현으로 바꾼다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemorySocialLoginStore.Config.class)
class ApiV1SocialAuthTest {
	private static final String STATE = "state-1";
	private static final String KAKAO_USER = "4000000001";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberFacade memberFacade;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private SocialAccountRepository socialAccountRepository;

	@Autowired
	private TermsAgreementRepository termsAgreementRepository;

	@Autowired
	private InMemorySocialLoginStore store;

	@MockitoBean
	private SocialOAuth2Client socialOAuth2Client;

	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	@BeforeEach
	void setUp() {
		given(socialOAuth2Client.isConfigured(any())).willReturn(true);
		given(socialOAuth2Client.begin(any(), any())).willAnswer(invocation -> {
			SocialProvider provider = invocation.getArgument(0);
			return new AuthorizationStart("https://provider.example/authorize?state=" + STATE,
				new StoredAuthorization(provider, STATE, "http://localhost/api/v1/auth/oauth2/callback/"
					+ provider.getPathValue(), Set.of("profile"), Map.of("code_verifier", "verifier"), null, null));
		});
	}

	@AfterEach
	void tearDown() {
		store.clear();
		socialAccountRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("시작하면 제공자 로그인 화면으로 보내고, 인가 요청 ID를 httpOnly 쿠키로 심고, 검사한 next를 저장한다")
	void authorizeRedirectsToProvider() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/auth/oauth2/authorize/kakao").param("next", "//evil.com"))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, "https://provider.example/authorize?state=" + STATE))
			.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
			.startsWith("oauth2Request=")
			.contains("Path=/api/v1/auth/oauth2", "Max-Age=600", "Secure", "HttpOnly", "SameSite=Lax");
		String requestId = result.getResponse().getCookie("oauth2Request").getValue();
		StoredAuthorization saved = store.takeAuthorization(requestId).orElseThrow();
		assertThat(saved.next()).isEqualTo("/");
		assertThat(saved.state()).isEqualTo(STATE);
		assertThat(saved.isLink()).isFalse();
	}

	@Test
	@DisplayName("키가 설정되지 않은 제공자로 시작하면 쿠키 없이 로그인 화면으로 보낸다")
	void authorizeFailsWhenNotConfigured() throws Exception {
		given(socialOAuth2Client.isConfigured(SocialProvider.GOOGLE)).willReturn(false);

		MvcResult result = mockMvc.perform(get("/api/v1/auth/oauth2/authorize/google"))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, "/login?error=AUTH_SOCIAL_FAILED"))
			.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
	}

	@Test
	@DisplayName("모르는 제공자는 404 AUTH_SOCIAL_PROVIDER_NOT_FOUND다")
	void unknownProvider() throws Exception {
		mockMvc.perform(get("/api/v1/auth/oauth2/authorize/naver"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("AUTH_SOCIAL_PROVIDER_NOT_FOUND"));
	}

	@Test
	@DisplayName("처음 온 사람은 가입 대기 쿠키를 받고 소셜 가입 화면으로 가며, 가입하면 비밀번호 없는 회원과 소셜 연결이 생기고 토큰을 받는다")
	void firstVisitorSignsUp() throws Exception {
		String requestId = authorize("kakao", "/races/12");
		identity(new SocialIdentity(SocialProvider.KAKAO, KAKAO_USER, "runner@kakao.com", null));

		MvcResult callback = callback("kakao", requestId, STATE)
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, "/signup/social?next=%2Fraces%2F12"))
			.andReturn();
		assertThat(callback.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
			.anyMatch(cookie -> cookie.startsWith("oauth2Request=;") && cookie.contains("Max-Age=0"))
			.anyMatch(cookie -> cookie.startsWith("oauth2Signup=") && cookie.contains("Max-Age=900"));
		String pendingId = callback.getResponse().getCookie("oauth2Signup").getValue();

		mockMvc.perform(get("/api/v1/auth/oauth2/signup").cookie(new Cookie("oauth2Signup", pendingId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.provider").value("KAKAO"))
			.andExpect(jsonPath("$.data.email").value("runner@kakao.com"))
			.andExpect(jsonPath("$.data.emailLocked").value(true));

		// 본문 이메일은 무시하고 제공자가 확인한 이메일을 쓴다
		MvcResult join = join(pendingId, "other@crewrun.com")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.data.member.email").value("runner@kakao.com"))
			.andExpect(jsonPath("$.data.member.socialProviders[0]").value("KAKAO"))
			.andExpect(jsonPath("$.data.member.passwordSet").value(false))
			.andReturn();
		assertThat(join.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
			.anyMatch(cookie -> cookie.startsWith("refreshToken=") && cookie.contains("Path=/api/v1/auth"))
			.anyMatch(cookie -> cookie.startsWith("oauth2Signup=;") && cookie.contains("Max-Age=0"));

		Member member = memberRepository.findActiveByEmail("runner@kakao.com").orElseThrow();
		assertThat(member.hasPassword()).isFalse();
		assertThat(socialAccountRepository.findActiveMemberId(SocialProvider.KAKAO, KAKAO_USER))
			.contains(member.getId());
		assertThat(store.findPendingSignup(pendingId)).isEmpty();
		verify(refreshTokenRepository).create(eq(member.getId()), any(NewFamily.class));
	}

	@Test
	@DisplayName("연결된 회원은 Refresh Token 쿠키를 받고 완료 화면으로 간다 (Access Token은 주소에 싣지 않는다)")
	void linkedMemberLogsIn() throws Exception {
		long memberId = socialMember();
		String requestId = authorize("kakao", "/me");
		identity(new SocialIdentity(SocialProvider.KAKAO, KAKAO_USER, null, null));

		MvcResult result = callback("kakao", requestId, STATE)
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, "/auth/social/complete?next=%2Fme"))
			.andReturn();

		assertThat(result.getResponse().getCookie("refreshToken").getValue()).isNotBlank();
		assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).doesNotContain("token");
		// 가입할 때 한 번, 이번 로그인에 한 번
		verify(refreshTokenRepository, times(2)).create(eq(memberId), any(NewFamily.class));
	}

	@Test
	@DisplayName("확인된 이메일이 이메일 회원 것이면 자동으로 연결하지 않고, 로그인 방법을 담아 로그인 화면으로 보낸다")
	void emailTakenIsRejected() throws Exception {
		joinEmailMember("runner1@crewrun.com");
		String requestId = authorize("google", "/");
		identity(new SocialIdentity(SocialProvider.GOOGLE, "google-sub-1", "runner1@crewrun.com", "김러너"));

		callback("google", requestId, STATE)
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION,
				"/login?error=AUTH_SOCIAL_EMAIL_TAKEN&provider=google&joinedWith=email"));

		assertThat(socialAccountRepository.count()).isZero();
	}

	@Test
	@DisplayName("state가 다르거나, 동의를 취소했거나, 쿠키가 없으면 제공자에 묻지 않고 실패 화면으로 보낸다")
	void callbackRejectsBadRequests() throws Exception {
		String requestId = authorize("kakao", "/");
		callback("kakao", requestId, "other-state")
			.andExpect(header().string(HttpHeaders.LOCATION, "/login?error=AUTH_SOCIAL_FAILED"));

		String cancelled = authorize("kakao", "/");
		mockMvc.perform(get("/api/v1/auth/oauth2/callback/kakao").param("error", "access_denied")
				.cookie(new Cookie("oauth2Request", cancelled)))
			.andExpect(header().string(HttpHeaders.LOCATION, "/login?error=AUTH_SOCIAL_FAILED"));

		mockMvc.perform(get("/api/v1/auth/oauth2/callback/kakao").param("code", "code").param("state", STATE))
			.andExpect(header().string(HttpHeaders.LOCATION, "/login?error=AUTH_SOCIAL_FAILED"));

		verify(socialOAuth2Client, never()).complete(any(), anyString(), anyString());
	}

	@Test
	@DisplayName("같은 콜백을 다시 열면 인가 요청이 이미 지워져 진행되지 않는다")
	void callbackCannotBeReplayed() throws Exception {
		String requestId = authorize("kakao", "/");
		identity(new SocialIdentity(SocialProvider.KAKAO, KAKAO_USER, null, null));
		callback("kakao", requestId, STATE).andExpect(status().isFound());

		callback("kakao", requestId, STATE)
			.andExpect(header().string(HttpHeaders.LOCATION, "/login?error=AUTH_SOCIAL_FAILED"));
	}

	@Test
	@DisplayName("제공자 응답이 잘못되면 실패 화면으로 보낸다")
	void providerFailure() throws Exception {
		String requestId = authorize("google", "/");
		given(socialOAuth2Client.complete(any(), anyString(), anyString()))
			.willThrow(new SocialLoginFailedException("소셜 로그인에 실패했습니다."));

		callback("google", requestId, STATE)
			.andExpect(header().string(HttpHeaders.LOCATION, "/login?error=AUTH_SOCIAL_FAILED"));
	}

	@Test
	@DisplayName("제공자가 이메일을 주지 않으면 가입 요청의 이메일을 쓰고, 그것도 없으면 400 AUTH_SOCIAL_EMAIL_REQUIRED다")
	void emailRequiredWhenProviderHasNone() throws Exception {
		String requestId = authorize("kakao", "/");
		identity(new SocialIdentity(SocialProvider.KAKAO, KAKAO_USER, null, null));
		String pendingId = callback("kakao", requestId, STATE).andReturn()
			.getResponse().getCookie("oauth2Signup").getValue();

		join(pendingId, null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_SOCIAL_EMAIL_REQUIRED"));

		join(pendingId, "typed@crewrun.com")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.member.email").value("typed@crewrun.com"));
	}

	@Test
	@DisplayName("카카오로 가입한 사람이 다른 이메일의 구글로 가입하면 닉네임보다 먼저 휴대폰 번호로 막고 409 AUTH_SOCIAL_PHONE_TAKEN이다")
	void phoneTakenGuidesToLink() throws Exception {
		String kakaoRequest = authorize("kakao", "/");
		identity(new SocialIdentity(SocialProvider.KAKAO, KAKAO_USER, "runner@kakao.com", null));
		String kakaoPending = callback("kakao", kakaoRequest, STATE).andReturn()
			.getResponse().getCookie("oauth2Signup").getValue();
		join(kakaoPending, null).andExpect(status().isCreated());

		String googleRequest = authorize("google", "/");
		identity(new SocialIdentity(SocialProvider.GOOGLE, "google-sub-1", "runner@gmail.com", null));
		String googlePending = callback("google", googleRequest, STATE).andReturn()
			.getResponse().getCookie("oauth2Signup").getValue();

		// 같은 닉네임·같은 번호로 보낸다. 닉네임 중복이 아니라 번호로 안내해야 계정 연결로 갈 수 있다
		join(googlePending, null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AUTH_SOCIAL_PHONE_TAKEN"));
		assertThat(socialAccountRepository.findActiveMemberId(SocialProvider.GOOGLE, "google-sub-1")).isEmpty();
		// 실패하면 가입 대기를 남긴다
		assertThat(store.findPendingSignup(googlePending)).isPresent();
	}

	@Test
	@DisplayName("가입 대기 쿠키가 없거나 지났으면 401 AUTH_SOCIAL_SIGNUP_EXPIRED다")
	void signupExpired() throws Exception {
		mockMvc.perform(get("/api/v1/auth/oauth2/signup"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_SOCIAL_SIGNUP_EXPIRED"));
		join("unknown", "typed@crewrun.com")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_SOCIAL_SIGNUP_EXPIRED"));
	}

	@Test
	@DisplayName("비밀번호 없는 소셜 회원이 이메일로 로그인하면 401 AUTH_SOCIAL_ACCOUNT로 가입한 제공자를 알려 준다")
	void socialMemberCannotUseEmailLogin() throws Exception {
		socialMember();
		// 가입할 때 발급한 토큰은 빼고, 이메일 로그인에서 발급하지 않는지만 본다
		clearInvocations(refreshTokenRepository);

		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "email": "runner@kakao.com", "password": "password1234" }
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_SOCIAL_ACCOUNT"))
			.andExpect(jsonPath("$.message").value("카카오로 가입한 계정입니다. 카카오로 로그인해주세요."));
		verify(refreshTokenRepository, never()).create(anyLong(), any(NewFamily.class));
	}

	// 카카오로 가입한 비밀번호 없는 회원을 만든다 (가입 흐름 그대로)
	private long socialMember() throws Exception {
		String requestId = authorize("kakao", "/");
		identity(new SocialIdentity(SocialProvider.KAKAO, KAKAO_USER, "runner@kakao.com", null));
		String pendingId = callback("kakao", requestId, STATE).andReturn()
			.getResponse().getCookie("oauth2Signup").getValue();
		join(pendingId, null).andExpect(status().isCreated());
		return memberRepository.findActiveByEmail("runner@kakao.com").orElseThrow().getId();
	}

	private void joinEmailMember(String email) {
		memberFacade.join(new MemberJoinRequestDto(email, "password1234", "김러너", "러너1", "010-1234-5678",
			LocalDate.of(1995, 4, 12), null, List.of(TermsType.SERVICE, TermsType.PRIVACY)));
	}

	private void identity(SocialIdentity identity) {
		given(socialOAuth2Client.complete(any(), anyString(), anyString())).willReturn(identity);
	}

	private String authorize(String provider, String next) throws Exception {
		return mockMvc.perform(get("/api/v1/auth/oauth2/authorize/" + provider).param("next", next))
			.andReturn().getResponse().getCookie("oauth2Request").getValue();
	}

	private ResultActions callback(String provider, String requestId, String state) throws Exception {
		return mockMvc.perform(get("/api/v1/auth/oauth2/callback/" + provider)
			.param("code", "authorization-code")
			.param("state", state)
			.cookie(new Cookie("oauth2Request", requestId)));
	}

	private ResultActions join(String pendingId, String email) throws Exception {
		String emailJson = email == null ? "null" : "\"" + email + "\"";
		return mockMvc.perform(post("/api/v1/auth/oauth2/join")
			.cookie(new Cookie("oauth2Signup", pendingId))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "email": %s, "name": "김소셜", "nickname": "소셜러너", "phone": "010-9876-5432",
					"birthDate": "1995-04-12", "agreedTerms": ["SERVICE", "PRIVACY"] }
				""".formatted(emailJson)));
	}
}
