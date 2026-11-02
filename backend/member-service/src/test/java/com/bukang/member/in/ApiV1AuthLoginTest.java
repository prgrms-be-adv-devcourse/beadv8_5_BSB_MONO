package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.jwt.JwtClaims;
import com.bukang.jwt.JwtProvider;
import com.bukang.jwt.TokenType;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.MemberJoinRequestDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.RefreshTokenRepository.NewFamily;
import com.bukang.member.out.TermsAgreementRepository;
import com.jayway.jsonpath.JsonPath;

// 테스트에서는 Redis 서버를 띄우지 않으므로 RefreshTokenRepository는 목으로 바꿔 저장 호출만 확인한다
// (Lua 스크립트 자체는 RefreshTokenRepositoryTest가 진짜 Redis로 확인한다)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiV1AuthLoginTest {
	private static final String EMAIL = "runner1@crewrun.com";
	private static final String PASSWORD = "password1234";

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

	private long memberId;

	@BeforeEach
	void setUp() {
		memberId = memberFacade.join(new MemberJoinRequestDto(
			EMAIL, PASSWORD, "김러너", "러너1", "010-1234-5678", LocalDate.of(1995, 4, 12), null,
			List.of(TermsType.SERVICE, TermsType.PRIVACY)
		)).getId();
	}

	@AfterEach
	void tearDown() {
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("로그인하면 Access Token은 본문으로, Refresh Token은 httpOnly 쿠키로 받고 새 family로 Redis에 저장된다")
	void loginIssuesTokens() throws Exception {
		MvcResult result = login(EMAIL, PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("로그인 성공"))
			.andExpect(jsonPath("$.data.member.id").value(memberId))
			.andExpect(jsonPath("$.data.member.email").value(EMAIL))
			.andReturn();

		String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
		JwtClaims accessClaims = jwtProvider.parse(accessToken, TokenType.ACCESS);
		assertThat(accessClaims.memberId()).isEqualTo(memberId);

		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie)
			.startsWith("refreshToken=")
			.contains("Path=/api/v1/auth", "Max-Age=" + Duration.ofDays(14).toSeconds(),
				"Secure", "HttpOnly", "SameSite=Lax");

		String refreshToken = result.getResponse().getCookie("refreshToken").getValue();
		JwtClaims refreshClaims = jwtProvider.parse(refreshToken, TokenType.REFRESH);
		assertThat(refreshClaims.memberId()).isEqualTo(memberId);
		assertThat(refreshClaims.familyId()).isNotBlank();

		ArgumentCaptor<NewFamily> saved = ArgumentCaptor.forClass(NewFamily.class);
		verify(refreshTokenRepository).create(eq((long)memberId), saved.capture());
		assertThat(saved.getValue().familyId()).isEqualTo(refreshClaims.familyId());
		assertThat(saved.getValue().jti()).isEqualTo(refreshClaims.jti());
		assertThat(saved.getValue().token()).isEqualTo(refreshToken);
		assertThat(Duration.between(saved.getValue().issuedAt(), saved.getValue().familyExpiresAt()))
			.isEqualTo(Duration.ofDays(30));
	}

	@Test
	@DisplayName("로그인해도 세션을 만들지 않는다 (로그인 상태는 토큰으로만 유지)")
	void loginCreatesNoSession() throws Exception {
		MvcResult result = login(EMAIL, PASSWORD)
			.andExpect(status().isOk())
			.andReturn();

		assertThat(result.getRequest().getSession(false)).isNull();
		assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
	}

	@Test
	@DisplayName("Redis에 저장하지 못해도 로그인은 성공하고 토큰을 받는다")
	void loginSucceedsWhenRedisIsDown() throws Exception {
		willThrow(new RedisConnectionFailureException("Redis 연결 실패"))
			.given(refreshTokenRepository).create(anyLong(), any(NewFamily.class));

		MvcResult result = login(EMAIL, PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
			.andReturn();

		assertThat(result.getResponse().getCookie("refreshToken")).isNotNull();
	}

	@Test
	@DisplayName("비밀번호가 틀리면 401이고 토큰을 발급하지 않는다")
	void loginFailsWithWrongPassword() throws Exception {
		MvcResult result = login(EMAIL, "wrong-password")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
			.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
		verify(refreshTokenRepository, never()).create(anyLong(), any(NewFamily.class));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "email": "%s", "password": "%s" }
				""".formatted(email, password)));
	}
}
