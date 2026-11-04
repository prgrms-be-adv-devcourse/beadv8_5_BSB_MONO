package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.member.event.MarketingConsentChangedEvent;
import com.bukang.member.app.MarketingUnsubscribeTokens;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.MemberOutboxEvent;
import com.bukang.member.domain.TermsAgreement;
import com.bukang.member.domain.TermsType;
import com.bukang.member.out.MemberOutboxEventRepository;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.support.MemberFixture;
import com.jayway.jsonpath.JsonPath;

/**
 * CRM 광고 메시지의 수신 거부 링크 발급(내부 API)과 수신 거부(로그인 없이 부르는 API) (BUK-75, BE-039)
 * - 토큰은 회원 ID.만료 시각.서명이고, 위조·변조·만료 토큰으로는 동의가 바뀌지 않는다
 * - 수신 거부는 마케팅 동의 철회로 남는다(기록 한 줄 + 동의 변경 이벤트). 두 번 눌러도 한 번만 남는다
 * 공개 API는 로그인하지 않고 부르므로 회원 ID 헤더를 넣지 않는다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1MarketingUnsubscribeTest {
	private static final String UNSUBSCRIBE = "/api/v1/member/marketing-consent/unsubscribe";
	private static final String LINK = "/internal/v1/member/members/{memberId}/marketing-unsubscribe-link";
	// application-test.yaml의 member.marketing.unsubscribe.front-base-url
	private static final String LINK_PREFIX = "http://localhost:3000/unsubscribe?token=";

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private MemberFacade memberFacade;
	@Autowired
	private MarketingUnsubscribeTokens tokens;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private TermsAgreementRepository termsAgreementRepository;
	@Autowired
	private MemberOutboxEventRepository memberOutboxEventRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private JsonConverter jsonConverter;

	private long memberId;
	private long otherMemberId;

	@BeforeEach
	void setUp() {
		List<TermsType> withMarketing = List.of(TermsType.SERVICE, TermsType.PRIVACY, TermsType.MARKETING);
		memberId = memberFacade.join(MemberFixture.joinRequest().agreedTerms(withMarketing).build(identityStore))
			.getId();
		otherMemberId = memberFacade.join(MemberFixture.joinRequest().agreedTerms(withMarketing).build(identityStore))
			.getId();
		memberOutboxEventRepository.deleteAll();
	}

	@AfterEach
	void tearDown() {
		memberOutboxEventRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("내부 API는 프론트 확인 화면 주소에 토큰을 붙인 링크와 1년 뒤 만료 시각을 돌려주고, 토큰에 회원 ID 말고는 없다")
	void issueLink() throws Exception {
		ResultActions result = mockMvc.perform(get(LINK, memberId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("수신 거부 링크를 발급했습니다."));

		String body = result.andReturn().getResponse().getContentAsString();
		String url = JsonPath.read(body, "$.data.url");
		assertThat(url).startsWith(LINK_PREFIX);
		String token = url.substring(LINK_PREFIX.length());
		assertThat(token.split("\\.")).hasSize(3);
		assertThat(token).startsWith(memberId + ".");
		LocalDateTime expiresAt = LocalDateTime.parse(JsonPath.read(body, "$.data.expiresAt"));
		assertThat(expiresAt).isCloseTo(LocalDateTime.now().plusDays(365), within(1, ChronoUnit.MINUTES));
	}

	@Test
	@DisplayName("발급한 링크의 토큰으로 수신 거부하면 200, 마케팅 동의 철회 기록 한 줄과 동의 변경 이벤트가 남는다")
	void unsubscribeWithIssuedToken() throws Exception {
		long before = countMarketing(memberId);

		unsubscribe(issuedToken(memberId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("광고 수신을 거부했습니다."));

		assertThat(countMarketing(memberId)).isEqualTo(before + 1);
		assertThat(latestMarketing(memberId).isAgreed()).isFalse();
		List<MemberOutboxEvent> rows = memberOutboxEventRepository.findAll();
		assertThat(rows).singleElement().satisfies(row -> {
			assertThat(row.getTopic()).isEqualTo(KafkaTopics.MARKETING_CONSENT_CHANGED);
			MarketingConsentChangedEvent event =
				jsonConverter.fromJson(row.getPayload(), MarketingConsentChangedEvent.class);
			assertThat(event.memberId()).isEqualTo(memberId);
			assertThat(event.agreed()).isFalse();
		});
		assertThat(latestMarketing(otherMemberId).isAgreed()).isTrue();
	}

	@Test
	@DisplayName("두 번 눌러도 둘 다 200이고, 철회 기록과 이벤트는 한 번만 남는다")
	void unsubscribeTwice() throws Exception {
		String token = issuedToken(memberId);
		long before = countMarketing(memberId);

		unsubscribe(token).andExpect(status().isOk());
		unsubscribe(token).andExpect(status().isOk());

		assertThat(countMarketing(memberId)).isEqualTo(before + 1);
		assertThat(memberOutboxEventRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("서명을 지어낸 토큰은 401 MEMBER_UNSUBSCRIBE_TOKEN_INVALID이고 동의가 바뀌지 않는다")
	void forgedSignature() throws Exception {
		String[] parts = issuedToken(memberId).split("\\.");
		String forged = parts[0] + "." + parts[1] + "." + "A".repeat(parts[2].length());

		unsubscribe(forged)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("MEMBER_UNSUBSCRIBE_TOKEN_INVALID"));

		assertNothingChanged();
	}

	@Test
	@DisplayName("회원 ID만 다른 회원으로 바꾼 토큰은 401이고, 그 다른 회원의 동의도 바뀌지 않는다")
	void tamperedMemberId() throws Exception {
		String[] parts = issuedToken(memberId).split("\\.");
		String tampered = otherMemberId + "." + parts[1] + "." + parts[2];

		unsubscribe(tampered)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("MEMBER_UNSUBSCRIBE_TOKEN_INVALID"));

		assertNothingChanged();
	}

	@Test
	@DisplayName("만료 시각만 늘린 토큰은 서명이 맞지 않아 401이다")
	void tamperedExpiry() throws Exception {
		String[] parts = tokens.issue(memberId, Instant.now().minusSeconds(60)).split("\\.");
		String extended = parts[0] + "." + (Long.parseLong(parts[1]) + 10_000_000L) + "." + parts[2];

		unsubscribe(extended)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("MEMBER_UNSUBSCRIBE_TOKEN_INVALID"));

		assertNothingChanged();
	}

	@Test
	@DisplayName("서명은 맞지만 기한이 지난 토큰은 410 MEMBER_UNSUBSCRIBE_TOKEN_EXPIRED이고 동의가 바뀌지 않는다")
	void expiredToken() throws Exception {
		String expired = tokens.issue(memberId, Instant.now().minusSeconds(1));

		unsubscribe(expired)
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("MEMBER_UNSUBSCRIBE_TOKEN_EXPIRED"));

		assertNothingChanged();
	}

	@Test
	@DisplayName("모양이 틀린 토큰(칸 수, 숫자 아님)은 400 MEMBER_UNSUBSCRIBE_TOKEN_MALFORMED, 빈 토큰은 400 INVALID_REQUEST")
	void malformedToken() throws Exception {
		for (String malformed : List.of("abc", "1.2", "x.123.sig", memberId + ".soon.sig", "1.2.3.4")) {
			unsubscribe(malformed)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MEMBER_UNSUBSCRIBE_TOKEN_MALFORMED"));
		}
		mockMvc.perform(post(UNSUBSCRIBE).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertNothingChanged();
	}

	@Test
	@DisplayName("탈퇴한 회원은 서명이 맞는 토큰이어도 수신 거부가 404이고, 링크 발급도 404다")
	void withdrawnMember() throws Exception {
		String token = issuedToken(memberId);
		transactionTemplate.executeWithoutResult(status ->
			memberRepository.findById(memberId).orElseThrow().withdraw(LocalDateTime.now()));
		memberOutboxEventRepository.deleteAll();
		long before = countMarketing(memberId);

		unsubscribe(token)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
		mockMvc.perform(get(LINK, memberId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
		mockMvc.perform(get(LINK, Long.MAX_VALUE))
			.andExpect(status().isNotFound());

		assertThat(countMarketing(memberId)).isEqualTo(before);
		assertThat(memberOutboxEventRepository.count()).isZero();
	}

	private String issuedToken(long id) throws Exception {
		String body = mockMvc.perform(get(LINK, id)).andReturn().getResponse().getContentAsString();
		String url = JsonPath.read(body, "$.data.url");
		return url.substring(LINK_PREFIX.length());
	}

	private ResultActions unsubscribe(String token) throws Exception {
		return mockMvc.perform(post(UNSUBSCRIBE)
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonConverter.toJson(new TokenBody(token))));
	}

	private void assertNothingChanged() {
		assertThat(latestMarketing(memberId).isAgreed()).isTrue();
		assertThat(latestMarketing(otherMemberId).isAgreed()).isTrue();
		assertThat(memberOutboxEventRepository.count()).isZero();
	}

	private List<TermsAgreement> marketingRows(long id) {
		return termsAgreementRepository.findByMemberId(id).stream()
			.filter(agreement -> agreement.getTerms().getType() == TermsType.MARKETING)
			.toList();
	}

	private long countMarketing(long id) {
		return marketingRows(id).size();
	}

	private TermsAgreement latestMarketing(long id) {
		return marketingRows(id).stream().max(Comparator.comparing(TermsAgreement::getId)).orElseThrow();
	}

	private record TokenBody(String token) {
	}
}
