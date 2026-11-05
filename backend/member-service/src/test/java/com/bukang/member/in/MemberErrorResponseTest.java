package com.bukang.member.in;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.exception.BusinessException;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;

/**
 * 오류 응답의 오류 코드(code)를 앱 전체를 띄워 확인한다.
 * 테스트용 컨트롤러(ErrorThrowingController)는 테스트 클래스 안의 클래스라 컴포넌트 스캔에서 빠지므로
 * @Import로 넣고, 그 컨트롤러의 프로파일을 이 테스트에서만 켠다
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles({"test", "member-error-response-test-only"})
@Import({MemberErrorResponseTest.ErrorThrowingController.class, InMemoryIdentityVerificationStore.Config.class})
class MemberErrorResponseTest {
	private static final String JOIN_BODY = """
		{
			"email": "%s",
			"password": "password1234",
			"nickname": "%s",
			"agreedTerms": ["SERVICE", "PRIVACY"],
			"verificationToken": "%s"
		}
		""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;

	@BeforeEach
	void joinBaseMember() throws Exception {
		mockMvc.perform(join("runner1@crewrun.com", "러너1", "010-1234-5678", "1995-04-12"))
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("이메일·닉네임·전화번호 중복은 모두 409지만 오류 코드로 구분된다")
	void duplicateCodes() throws Exception {
		mockMvc.perform(join("runner1@crewrun.com", "러너2", "010-2222-3333", "1995-04-12"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.code").value("MEMBER_DUPLICATE_EMAIL"))
			.andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
		mockMvc.perform(join("runner2@crewrun.com", "러너1", "010-2222-3333", "1995-04-12"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEMBER_DUPLICATE_NICKNAME"));
		mockMvc.perform(join("runner2@crewrun.com", "러너2", "01012345678", "1995-04-12"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEMBER_DUPLICATE_PHONE"));
	}

	@Test
	@DisplayName("같은 사람(CI 해시)이 다시 가입하면 MEMBER_DUPLICATE_IDENTITY로 응답한다")
	void duplicateIdentity() throws Exception {
		String token = identityStore.verify("김러너", LocalDate.of(1995, 4, 12), "010-3333-4444", "same-ci");
		mockMvc.perform(post("/api/v1/auth/join")
				.contentType(MediaType.APPLICATION_JSON)
				.content(JOIN_BODY.formatted("first@crewrun.com", "첫계정", token)))
			.andExpect(status().isCreated());
		String again = identityStore.verify("김러너", LocalDate.of(1995, 4, 12), "010-3333-5555", "same-ci");
		mockMvc.perform(post("/api/v1/auth/join")
				.contentType(MediaType.APPLICATION_JSON)
				.content(JOIN_BODY.formatted("second@crewrun.com", "둘째계정", again)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEMBER_DUPLICATE_IDENTITY"));
	}

	@Test
	@DisplayName("본인인증 토큰이 저장소에 없으면 AUTH_VERIFICATION_REQUIRED로 응답한다")
	void verificationRequired() throws Exception {
		mockMvc.perform(post("/api/v1/auth/join")
				.contentType(MediaType.APPLICATION_JSON)
				.content(JOIN_BODY.formatted("nobody@crewrun.com", "토큰없음", "crewrun-iv-unknown")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_VERIFICATION_REQUIRED"));
	}

	@Test
	@DisplayName("만 14세 미만 가입은 MEMBER_UNDER_AGE로 응답한다")
	void underAge() throws Exception {
		mockMvc.perform(join("kid@crewrun.com", "꼬마", "010-2222-3333", "2020-01-01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MEMBER_UNDER_AGE"));
	}

	@Test
	@DisplayName("입력값 검증 실패는 INVALID_REQUEST로 응답한다")
	void invalidRequest() throws Exception {
		mockMvc.perform(join("not-an-email", "러너2", "010-2222-3333", "1995-04-12"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("로그인 실패는 MEMBER_LOGIN_FAILED로 응답한다")
	void loginFailed() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "email": "runner1@crewrun.com", "password": "wrong-password" }
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("MEMBER_LOGIN_FAILED"));
	}

	@Test
	@DisplayName("Refresh Token으로 재발급하지 못하면 MEMBER_INVALID_REFRESH_TOKEN으로 응답한다")
	void invalidRefreshToken() throws Exception {
		mockMvc.perform(post("/api/v1/auth/reissue"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("MEMBER_INVALID_REFRESH_TOKEN"));
	}

	@Test
	@DisplayName("예상 못 한 오류는 RsData 형식의 500 INTERNAL_ERROR로 응답한다")
	void unexpectedError() throws Exception {
		mockMvc.perform(get("/test/unexpected"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.status").value(500))
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."))
			.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	@DisplayName("없는 주소(404)와 지원하지 않는 메서드(405)는 500으로 바뀌지 않는다")
	void notFoundAndMethodNotAllowedKeepStatus() throws Exception {
		mockMvc.perform(get("/test/no-such-path"))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/test/post-only"))
			.andExpect(status().isMethodNotAllowed());
	}

	@Test
	@DisplayName("코드를 넘기지 않은 도메인 예외는 응답에 code 필드가 없다")
	void businessExceptionWithoutCode() throws Exception {
		mockMvc.perform(get("/test/without-code"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.message").value("코드 없는 예외"))
			// doesNotExist()는 값이 null인 필드도 통과시키므로, 필드 자체가 없는지 키로 확인한다
			.andExpect(jsonPath("$", not(hasKey("code"))))
			.andExpect(jsonPath("$", hasKey("data")));
	}

	// 이름 · 휴대폰 번호 · 생년월일은 본인인증 결과로 넣는다. CI 해시는 매번 다르다
	// 이름은 닉네임마다 달리 해, 이메일 · 닉네임 · 번호 중복이 1인 1계정 검사보다 먼저 걸리지 않게 한다
	private RequestBuilder join(
		String email,
		String nickname,
		String phone,
		String birthDate
	) {
		String token = identityStore.verify("김" + nickname, LocalDate.parse(birthDate), phone,
			"ci-" + UUID.randomUUID());
		return post("/api/v1/auth/join")
			.contentType(MediaType.APPLICATION_JSON)
			.content(JOIN_BODY.formatted(email, nickname, token));
	}

	@Profile("member-error-response-test-only")
	@RestController
	static class ErrorThrowingController {
		@GetMapping("/test/unexpected")
		void unexpected() {
			throw new IllegalStateException("예상 못 한 오류");
		}

		@PostMapping("/test/post-only")
		void postOnly() {
		}

		@GetMapping("/test/without-code")
		void withoutCode() {
			throw new CodelessException();
		}
	}

	static class CodelessException extends BusinessException {
		CodelessException() {
			super(HttpStatus.CONFLICT, "코드 없는 예외");
		}
	}
}
