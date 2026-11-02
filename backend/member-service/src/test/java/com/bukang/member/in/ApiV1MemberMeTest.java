package com.bukang.member.in;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.MemberJoinRequestDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;

// 게이트웨이가 토큰을 검사하고 넣어 주는 X-Member-Id 헤더를 테스트에서는 직접 넣는다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiV1MemberMeTest {
	private static final String ME = "/api/v1/member/members/me";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberFacade memberFacade;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private TermsAgreementRepository termsAgreementRepository;

	private long memberId;

	@BeforeEach
	void setUp() {
		memberId = memberFacade.join(new MemberJoinRequestDto(
			"runner1@crewrun.com", "password1234", "김러너", "러너1", "010-1234-5678", LocalDate.of(1995, 4, 12), null,
			List.of(TermsType.SERVICE, TermsType.PRIVACY)
		)).getId();
	}

	@AfterEach
	void tearDown() {
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("회원 ID 헤더의 회원 정보를 수정 화면에 채울 이름·생년월일까지 돌려준다")
	void findMe() throws Exception {
		mockMvc.perform(get(ME).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("내 정보를 조회했습니다."))
			.andExpect(jsonPath("$.data.id").value(memberId))
			.andExpect(jsonPath("$.data.nickname").value("러너1"))
			.andExpect(jsonPath("$.data.email").value("runner1@crewrun.com"))
			.andExpect(jsonPath("$.data.phone").value("010-1234-5678"))
			.andExpect(jsonPath("$.data.name").value("김러너"))
			.andExpect(jsonPath("$.data.birthDate").value("1995-04-12"));
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 로그인하지 않은 요청으로 보고 401")
	void findMeWithoutHeader() throws Exception {
		mockMvc.perform(get(ME))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("헤더의 회원이 없으면(탈퇴 등) 404")
	void findMeWithUnknownMember() throws Exception {
		mockMvc.perform(get(ME).header(MemberIdHeader.NAME, memberId + 1000))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 회원입니다."));
	}
}
