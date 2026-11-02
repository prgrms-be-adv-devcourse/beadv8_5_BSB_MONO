package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.MemberJoinRequestDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.RefreshTokenRepository.NewFamily;
import com.bukang.member.out.RefreshTokenRepository.Rotation;
import com.bukang.member.out.RotationOutcome;
import com.bukang.member.out.RotationResult;
import com.bukang.member.out.TermsAgreementRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

// 테스트에서는 Redis 서버를 띄우지 않으므로 RefreshTokenRepository를 메모리 Map으로 흉내 낸다
// 흉내는 컨트롤러·쿠키 흐름을 보려는 최소한이라 유예(GRACE)는 넣지 않는다. 판정 전체는 RefreshTokenRepositoryTest가 진짜 Redis로 확인한다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiV1AuthReissueLogoutTest {
	private static final String EMAIL = "runner1@crewrun.com";
	private static final String PASSWORD = "password1234";
	private static final String INVALID_MESSAGE = "유효하지 않은 Refresh Token입니다. 다시 로그인해주세요.";

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

	private final Map<Long, Saved> redis = new HashMap<>();
	private long memberId;

	@BeforeEach
	void setUp() {
		willAnswer(invocation -> {
			NewFamily family = invocation.getArgument(1);
			return redis.put(invocation.getArgument(0),
				new Saved(family.familyId(), family.jti(), family.familyExpiresAt()));
		}).given(refreshTokenRepository).create(anyLong(), any(NewFamily.class));
		willAnswer(invocation -> Optional.ofNullable(redis.get(invocation.<Long>getArgument(0)))
			.map(Saved::familyExpiresAt))
			.given(refreshTokenRepository).findFamilyExpiresAt(anyLong());
		willAnswer(invocation -> rotate(invocation.getArgument(0), invocation.getArgument(1)))
			.given(refreshTokenRepository).rotate(anyLong(), any(Rotation.class));
		willAnswer(invocation -> deleteIfFamily(invocation.getArgument(0), invocation.getArgument(1)))
			.given(refreshTokenRepository).deleteIfFamily(anyLong(), anyString());

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
	@DisplayName("유효한 Refresh Token 쿠키로 새 Access Token은 본문으로, 새 Refresh Token은 쿠키로 받는다")
	void reissue() throws Exception {
		String refreshToken = loginAndGetRefreshToken();

		MvcResult result = reissue(refreshToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("토큰 재발급 성공"))
			.andReturn();

		String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
		assertThat(jwtProvider.parse(accessToken, TokenType.ACCESS).memberId()).isEqualTo(memberId);

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
			.startsWith("refreshToken=")
			.contains("Path=/api/v1/auth", "Secure", "HttpOnly", "SameSite=Lax");
		String rotated = result.getResponse().getCookie("refreshToken").getValue();
		assertThat(rotated).isNotEqualTo(refreshToken);
		assertThat(jwtProvider.parse(rotated, TokenType.REFRESH).familyId())
			.isEqualTo(jwtProvider.parse(refreshToken, TokenType.REFRESH).familyId());
	}

	@Test
	@DisplayName("회전한 뒤 이전 Refresh Token을 다시 쓰면 401이고, 그 로그인은 끊긴다")
	void reuseAfterRotationRejected() throws Exception {
		String refreshToken = loginAndGetRefreshToken();
		String rotated = reissue(refreshToken).andExpect(status().isOk())
			.andReturn().getResponse().getCookie("refreshToken").getValue();

		reissue(refreshToken)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
		reissue(rotated).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Refresh Token 쿠키가 없으면 401이다")
	void reissueWithoutCookie() throws Exception {
		mockMvc.perform(post("/api/v1/auth/reissue"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Refresh Token이 없습니다. 다시 로그인해주세요."));
	}

	@Test
	@DisplayName("위조된 Refresh Token이면 401이다")
	void reissueWithInvalidToken() throws Exception {
		reissue("not-a-jwt")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
	}

	@Test
	@DisplayName("로그아웃하면 Redis의 기록이 지워지고 쿠키가 만료되며, 이후 재발급은 401이다")
	void logoutThenReissueRejected() throws Exception {
		String refreshToken = loginAndGetRefreshToken();

		MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")
				.cookie(new Cookie("refreshToken", refreshToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("로그아웃 성공"))
			.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
			.startsWith("refreshToken=;")
			.contains("Path=/api/v1/auth", "Max-Age=0", "HttpOnly");
		assertThat(redis).doesNotContainKey((long)memberId);

		reissue(refreshToken)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
	}

	@Test
	@DisplayName("다른 기기에서 다시 로그인한 뒤 이전 기기가 로그아웃해도 새 로그인은 남는다")
	void logoutWithOldFamilyKeepsNewLogin() throws Exception {
		String oldDeviceToken = loginAndGetRefreshToken();
		String newDeviceToken = loginAndGetRefreshToken();

		mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("refreshToken", oldDeviceToken)))
			.andExpect(status().isOk());

		assertThat(redis).containsKey((long)memberId);
		reissue(newDeviceToken).andExpect(status().isOk());
	}

	@Test
	@DisplayName("쿠키 없이 로그아웃해도 성공하고 쿠키 만료를 내려준다")
	void logoutWithoutCookie() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/logout"))
			.andExpect(status().isOk())
			.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
	}

	// refresh-token-rotate.lua를 컨트롤러 흐름에 필요한 만큼만 흉내 낸다 (유예 없음)
	private RotationResult rotate(long id, Rotation rotation) {
		Saved saved = redis.get(id);
		if (saved == null) {
			return new RotationResult(RotationOutcome.MISSING, null);
		}
		if (!saved.familyId().equals(rotation.familyId())) {
			return new RotationResult(RotationOutcome.STALE, null);
		}
		if (saved.jti().equals(rotation.presentedJti())) {
			redis.put(id, new Saved(saved.familyId(), rotation.newJti(), saved.familyExpiresAt()));
			return new RotationResult(RotationOutcome.ROTATED, null);
		}
		redis.remove(id);
		return new RotationResult(RotationOutcome.THEFT, null);
	}

	// refresh-token-delete-family.lua 흉내
	private boolean deleteIfFamily(long id, String familyId) {
		Saved saved = redis.get(id);
		if (saved != null && saved.familyId().equals(familyId)) {
			redis.remove(id);
			return true;
		}
		return false;
	}

	private record Saved(String familyId, String jti, Instant familyExpiresAt) {
	}

	private String loginAndGetRefreshToken() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "email": "%s", "password": "%s" }
					""".formatted(EMAIL, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn();
		return result.getResponse().getCookie("refreshToken").getValue();
	}

	private ResultActions reissue(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/reissue").cookie(new Cookie("refreshToken", refreshToken)));
	}
}
