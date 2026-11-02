package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
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

import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.TermsType;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;

// 로그인 전에 부르는 API라 X-Member-Id 헤더를 넣지 않는다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1MemberEmailAvailabilityTest {
	@Autowired
	private InMemoryIdentityVerificationStore identityStore;

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
		memberFacade.join(identityStore.joinRequest(
			EMAIL, "password1234", "김러너", "러너1", "010-1234-5678", LocalDate.of(1995, 4, 12), null,
			List.of(TermsType.SERVICE, TermsType.PRIVACY)
		));

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
}
