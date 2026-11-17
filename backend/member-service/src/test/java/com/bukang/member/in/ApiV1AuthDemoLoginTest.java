package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

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

import com.bukang.jwt.JwtProvider;
import com.bukang.jwt.TokenType;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.Member;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.RefreshTokenRepository.NewFamily;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.demologin.InMemoryDemoLoginStore;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.support.MemberFixture;
import com.jayway.jsonpath.JsonPath;

// 데모 로그인이 켜진 상태. 로그인할 계정은 설정에 적은 이메일 하나뿐이다
@SpringBootTest(properties = {
	"member.auth.demo-login.enabled=true",
	"member.auth.demo-login.email=" + ApiV1AuthDemoLoginTest.DEMO_EMAIL
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({InMemoryIdentityVerificationStore.Config.class, InMemoryDemoLoginStore.Config.class})
class ApiV1AuthDemoLoginTest {
	static final String DEMO_EMAIL = "demo-admin@crewrun.com";
	private static final String OTHER_EMAIL = "runner1@crewrun.com";
	private static final String PATH = "/api/v1/auth/demo-login";

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;

	@Autowired
	private InMemoryDemoLoginStore demoLoginStore;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberFacade memberFacade;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private TermsAgreementRepository termsAgreementRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	private long demoMemberId;
	private long otherMemberId;

	@BeforeEach
	void setUp() {
		demoLoginStore.clear();
		demoMemberId = memberFacade.join(MemberFixture.joinRequest().email(DEMO_EMAIL).build(identityStore)).getId();
		otherMemberId = memberFacade.join(MemberFixture.joinRequest().email(OTHER_EMAIL).build(identityStore)).getId();
	}

	@AfterEach
	void tearDown() {
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("켜져 있으면 GET은 200이다 (프론트가 버튼을 보여 줄지 정한다)")
	void availabilityIsOkWhenEnabled() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200));
	}

	@Test
	@DisplayName("설정에 적은 계정으로 로그인하고, 이메일 로그인과 같은 모양으로 토큰을 내려 주며 새 family를 저장한다")
	void logsInConfiguredAccount() throws Exception {
		MvcResult result = demoLogin()
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("로그인 성공"))
			.andExpect(jsonPath("$.data.member.id").value(demoMemberId))
			.andExpect(jsonPath("$.data.member.email").value(DEMO_EMAIL))
			.andReturn();

		String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
		assertThat(jwtProvider.parse(accessToken, TokenType.ACCESS).memberId()).isEqualTo(demoMemberId);

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
			.startsWith("refreshToken=")
			.contains("Path=/api/v1/auth", "Secure", "HttpOnly", "SameSite=Lax");
		String refreshToken = result.getResponse().getCookie("refreshToken").getValue();
		assertThat(jwtProvider.parse(refreshToken, TokenType.REFRESH).memberId()).isEqualTo(demoMemberId);

		// 이메일 로그인과 같은 발급 경로라 Refresh Token family가 저장되어 회전·재사용 탐지를 그대로 받는다
		verify(refreshTokenRepository).create(eq(demoMemberId), any(NewFamily.class));
	}

	@Test
	@DisplayName("요청 본문에 다른 이메일·회원 ID를 넣어도 설정에 적은 계정으로만 로그인한다")
	void ignoresRequestBody() throws Exception {
		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "email": "%s", "memberId": %d, "password": "anything" }
					""".formatted(OTHER_EMAIL, otherMemberId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.member.id").value(demoMemberId));

		verify(refreshTokenRepository, never()).create(eq(otherMemberId), any(NewFamily.class));
	}

	@Test
	@DisplayName("쿼리 파라미터·X-Member-Id 헤더로도 다른 계정을 고를 수 없다")
	void ignoresQueryAndHeader() throws Exception {
		mockMvc.perform(post(PATH)
				.param("email", OTHER_EMAIL)
				.header("X-Member-Id", otherMemberId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.member.id").value(demoMemberId));
	}

	@Test
	@DisplayName("설정에 적은 계정이 탈퇴했으면 토큰을 주지 않는다")
	void refusesWithdrawnAccount() throws Exception {
		Member demo = memberRepository.findById(demoMemberId).orElseThrow();
		demo.withdraw(LocalDateTime.now());
		memberRepository.save(demo);

		MvcResult result = demoLogin()
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
			.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
		verify(refreshTokenRepository, never()).create(anyLong(), any(NewFamily.class));
	}

	@Test
	@DisplayName("같은 IP는 1분에 10번까지 받고 11번째는 429이며 토큰을 주지 않는다")
	void limitsPerIp() throws Exception {
		for (int i = 0; i < 10; i++) {
			demoLogin("203.0.113.7").andExpect(status().isOk());
		}

		MvcResult result = demoLogin("203.0.113.7")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("DEMO_LOGIN_TOO_MANY"))
			.andExpect(jsonPath("$.message").value("요청이 너무 많습니다. 잠시 뒤 다시 시도해주세요."))
			.andReturn();
		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();

		// 다른 IP는 따로 센다
		demoLogin("198.51.100.9").andExpect(status().isOk());
	}

	@Test
	@DisplayName("X-Forwarded-For를 바꿔도 X-Real-IP로 세므로 한도를 피할 수 없다")
	void forgedForwardedForCannotBypass() throws Exception {
		for (int i = 0; i < 10; i++) {
			mockMvc.perform(post(PATH).header("X-Real-IP", "203.0.113.7").header("X-Forwarded-For", "10.0.0." + i))
				.andExpect(status().isOk());
		}

		mockMvc.perform(post(PATH).header("X-Real-IP", "203.0.113.7").header("X-Forwarded-For", "10.0.0.99"))
			.andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName("로그인해도 세션을 만들지 않는다")
	void createsNoSession() throws Exception {
		MvcResult result = demoLogin().andExpect(status().isOk()).andReturn();

		assertThat(result.getRequest().getSession(false)).isNull();
	}

	private ResultActions demoLogin() throws Exception {
		return mockMvc.perform(post(PATH));
	}

	private ResultActions demoLogin(String ip) throws Exception {
		return mockMvc.perform(post(PATH).header("X-Real-IP", ip));
	}
}
