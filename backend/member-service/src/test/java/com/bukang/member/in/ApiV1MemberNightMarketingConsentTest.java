package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.TermsType;
import com.bukang.member.out.MemberOutboxEventRepository;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.support.MemberFixture;

/**
 * 야간 광고 수신(밤 9시~아침 8시) 동의 조회·변경. 마케팅 동의와 같은 방식이고, 서로 따로 움직인다
 * 이 동의는 받는 서비스가 없어 동의 변경 이벤트는 보내지 않는다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1MemberNightMarketingConsentTest {
	private static final String NIGHT = "/api/v1/member/members/me/night-marketing-consent";
	private static final String MARKETING = "/api/v1/member/members/me/marketing-consent";

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private MemberFacade memberFacade;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private TermsAgreementRepository termsAgreementRepository;
	@Autowired
	private MemberOutboxEventRepository memberOutboxEventRepository;

	private long memberId;

	@BeforeEach
	void setUp() {
		memberId = memberFacade.join(MemberFixture.joinRequest()
			.agreedTerms(List.of(TermsType.SERVICE, TermsType.PRIVACY, TermsType.AGE_OVER_14,
				TermsType.MARKETING))
			.build(identityStore)).getId();
		memberOutboxEventRepository.deleteAll();
	}

	@AfterEach
	void tearDown() {
		memberOutboxEventRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("가입 때 동의하지 않았으면 조회는 agreed=false다")
	void findDefaultsToFalse() throws Exception {
		mockMvc.perform(get(NIGHT).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.agreed").value(false));
	}

	@Test
	@DisplayName("켜면 야간 광고 동의 기록이 새 줄로 쌓이고 마케팅 동의 기록은 그대로이며 이벤트는 남지 않는다")
	void turnOnAppendsOnlyNightRow() throws Exception {
		long marketingBefore = count(TermsType.MARKETING);
		long nightBefore = count(TermsType.NIGHT_MARKETING);

		change(memberId, "{\"agreed\": true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.agreed").value(true))
			.andExpect(jsonPath("$.data.decidedAt").isNotEmpty());

		assertThat(count(TermsType.NIGHT_MARKETING)).isEqualTo(nightBefore + 1);
		assertThat(count(TermsType.MARKETING)).isEqualTo(marketingBefore);
		mockMvc.perform(get(MARKETING).header(MemberIdHeader.NAME, memberId))
			.andExpect(jsonPath("$.data.agreed").value(true));
		assertThat(memberOutboxEventRepository.count()).isZero();
	}

	@Test
	@DisplayName("같은 값이면 기록이 늘지 않는다")
	void sameValueChangesNothing() throws Exception {
		change(memberId, "{\"agreed\": true}").andExpect(status().isOk());
		long before = count(TermsType.NIGHT_MARKETING);

		change(memberId, "{\"agreed\": true}").andExpect(status().isOk());

		assertThat(count(TermsType.NIGHT_MARKETING)).isEqualTo(before);
	}

	@Test
	@DisplayName("agreed가 없으면 400, 헤더가 없으면 401, 없는 회원은 404다")
	void invalidAndUnauthorized() throws Exception {
		change(memberId, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mockMvc.perform(get(NIGHT)).andExpect(status().isUnauthorized());
		change(Long.MAX_VALUE, "{\"agreed\": true}").andExpect(status().isNotFound());
	}

	private ResultActions change(long id, String body) throws Exception {
		return mockMvc.perform(put(NIGHT)
			.header(MemberIdHeader.NAME, id)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private long count(TermsType type) {
		return termsAgreementRepository.findByMemberId(memberId).stream()
			.filter(agreement -> agreement.getTerms().getType() == type)
			.count();
	}
}
