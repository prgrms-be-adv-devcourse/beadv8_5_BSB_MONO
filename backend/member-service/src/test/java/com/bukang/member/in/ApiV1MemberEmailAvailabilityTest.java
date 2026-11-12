package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.bukang.member.app.MemberFacade;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.emailcheck.InMemoryEmailCheckStore;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.support.MemberFixture;

// 로그인 전에 부르는 API라 X-Member-Id 헤더를 넣지 않는다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({InMemoryIdentityVerificationStore.Config.class, InMemoryEmailCheckStore.Config.class})
class ApiV1MemberEmailAvailabilityTest {
	@Autowired
	private InMemoryIdentityVerificationStore identityStore;

	@Autowired
	private InMemoryEmailCheckStore emailCheckStore;

	private static final String PATH = "/api/v1/member/members/email-availability";
	private static final String EMAIL = "runner1@crewrun.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberFacade memberFacade;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private TermsAgreementRepository termsAgreementRepository;

	// 같은 접속 주소(127.0.0.1)로 여러 테스트가 부르므로 횟수를 비운다
	@BeforeEach
	void resetLimit() {
		emailCheckStore.clear();
	}

	// 동의 기록이 회원을 가리키므로(FK) 먼저 지운다
	@AfterEach
	void tearDown() {
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("가입되지 않은 이메일이면 available이 true다 (회원 ID 헤더 없이도 부를 수 있다)")
	void availableWhenUnused() throws Exception {
		mockMvc.perform(get(PATH).param("email", EMAIL))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("이메일 사용 가능 여부를 확인했습니다."))
			.andExpect(jsonPath("$.data.available").value(true));
	}

	@Test
	@DisplayName("이미 가입된 이메일이면 409가 아니라 200에 available이 false다")
	void unavailableWhenExists() throws Exception {
		memberFacade.join(MemberFixture.joinRequest().email(EMAIL).build(identityStore));

		mockMvc.perform(get(PATH).param("email", EMAIL))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.available").value(false));
	}

	@ParameterizedTest
	@ValueSource(strings = {"not-an-email", "runner1@", "runner 1@crewrun.com"})
	@DisplayName("이메일 형식이 아니면 400 INVALID_REQUEST다")
	void badFormatIsInvalidRequest(String email) throws Exception {
		mockMvc.perform(get(PATH).param("email", email))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("이메일 형식에 맞게 작성해주세요."));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   "})
	@DisplayName("이메일이 비어 있으면 400 INVALID_REQUEST다")
	void blankIsInvalidRequest(String email) throws Exception {
		mockMvc.perform(get(PATH).param("email", email))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("이메일 파라미터가 없으면 400 INVALID_REQUEST다")
	void missingIsInvalidRequest() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("이메일을 작성해주세요."));
	}

	@Test
	@DisplayName("이메일이 255자를 넘으면 400 INVALID_REQUEST다")
	void tooLongIsInvalidRequest() throws Exception {
		// 형식은 맞고 길이만 넘게 만든다. 형식 검사(@ 앞 64자, 도메인 칸 63자)에도 걸리면
		// 두 오류 중 어느 문구가 먼저 나올지 정해지지 않아 테스트가 흔들린다
		String longEmail = "a".repeat(64) + "@" + "b".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(59) + ".com";
		assertThat(longEmail).hasSize(256);

		mockMvc.perform(get(PATH).param("email", longEmail))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("이메일은 255자 이하로 작성해주세요."));
	}

	private ResultActions check(String ip) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.get(PATH).param("email", EMAIL).header("X-Real-IP", ip));
	}

	@Test
	@DisplayName("같은 IP는 10번까지 받고 11번째는 429이며, 문구와 형식은 재설정 429를 따른다")
	void limitsPerIp() throws Exception {
		for (int i = 0; i < 10; i++) {
			check("203.0.113.7").andExpect(status().isOk());
		}

		check("203.0.113.7")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.code").value("EMAIL_CHECK_TOO_MANY"))
			.andExpect(jsonPath("$.message").value("요청이 너무 많습니다. 잠시 뒤 다시 시도해주세요."))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("한 IP가 한도를 넘어도 다른 IP의 요청은 받는다")
	void otherIpStillAllowed() throws Exception {
		for (int i = 0; i < 11; i++) {
			check("203.0.113.7");
		}

		check("198.51.100.9").andExpect(status().isOk());
	}

	@Test
	@DisplayName("X-Forwarded-For를 바꿔도 X-Real-IP로 세므로 한도를 피할 수 없다")
	void forgedForwardedForCannotBypass() throws Exception {
		for (int i = 0; i < 10; i++) {
			mockMvc.perform(MockMvcRequestBuilders.get(PATH).param("email", EMAIL)
				.header("X-Real-IP", "203.0.113.7").header("X-Forwarded-For", "10.0.0." + i))
				.andExpect(status().isOk());
		}

		mockMvc.perform(MockMvcRequestBuilders.get(PATH).param("email", EMAIL)
			.header("X-Real-IP", "203.0.113.7").header("X-Forwarded-For", "10.0.0.99"))
			.andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName("429 응답은 가입 여부를 알려 주지 않는다 (가입된 이메일도 같은 응답)")
	void tooManyResponseHidesMembership() throws Exception {
		memberFacade.join(MemberFixture.joinRequest().email(EMAIL).build(identityStore));
		for (int i = 0; i < 10; i++) {
			check("203.0.113.7");
		}

		check("203.0.113.7")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.data").doesNotExist());
	}
}
