package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.member.event.MarketingConsentChangedEvent;
import com.bukang.common.shared.member.web.MemberIdHeader;
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
 * 마이페이지 마케팅 수신 동의 조회·변경 (BUK-70)
 * - 바꾸면 동의 기록이 새 줄로 쌓이고, 같은 트랜잭션에서 동의 변경 이벤트가 아웃박스에 남는다
 * - 같은 값이면 기록·이벤트를 남기지 않는다 (동의 시각이 처음 동의한 날로 유지된다)
 * 게이트웨이가 넣어 주는 X-Member-Id 헤더는 테스트에서 직접 넣는다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1MemberMarketingConsentTest {
	private static final String CONSENT = "/api/v1/member/members/me/marketing-consent";

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
	// 아웃박스 저장이 실패하는 경우를 만들려고 감싼다. 평소에는 진짜 저장소로 동작한다
	@MockitoSpyBean
	private MemberOutboxEventRepository memberOutboxEventRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private JsonConverter jsonConverter;

	private long agreedMemberId;
	private long notAgreedMemberId;

	@BeforeEach
	void setUp() {
		agreedMemberId = memberFacade.join(MemberFixture.joinRequest()
			.agreedTerms(List.of(TermsType.SERVICE, TermsType.PRIVACY, TermsType.MARKETING))
			.build(identityStore)).getId();
		notAgreedMemberId = memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
		// 가입 이벤트 행은 세지 않도록 비우고 시작한다
		memberOutboxEventRepository.deleteAll();
	}

	@AfterEach
	void tearDown() {
		memberOutboxEventRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("가입 때 마케팅에 동의한 회원은 agreed=true와 가입 때 동의 시각을 받는다")
	void findAgreed() throws Exception {
		LocalDateTime joinedDecidedAt = latestMarketing(agreedMemberId).getDecidedAt();

		ResultActions result = find(agreedMemberId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("마케팅 수신 동의 상태를 조회했습니다."))
			.andExpect(jsonPath("$.data.agreed").value(true));

		assertThat(decidedAtOf(result)).isEqualTo(joinedDecidedAt);
	}

	@Test
	@DisplayName("가입 때 마케팅에 동의하지 않은 회원은 agreed=false를 받는다")
	void findNotAgreed() throws Exception {
		find(notAgreedMemberId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.agreed").value(false));
	}

	@Test
	@DisplayName("동의를 끄면 200, 동의 기록이 새 줄로 쌓이고, 그 뒤 조회하면 agreed=false다")
	void turnOffAppendsAgreement() throws Exception {
		long before = countMarketing(agreedMemberId);

		change(agreedMemberId, "{\"agreed\": false}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("마케팅 수신 동의를 변경했습니다."))
			.andExpect(jsonPath("$.data.agreed").value(false))
			.andExpect(jsonPath("$.data.decidedAt").isNotEmpty());

		assertThat(countMarketing(agreedMemberId)).isEqualTo(before + 1);
		assertThat(latestMarketing(agreedMemberId).isAgreed()).isFalse();
		find(agreedMemberId).andExpect(jsonPath("$.data.agreed").value(false));
	}

	@Test
	@DisplayName("동의를 바꾸면 동의 변경 토픽 행이 아웃박스에 남고, 본문의 회원 ID·동의 여부·바꾼 시각이 기록과 같다")
	void changeAppendsOutboxEvent() throws Exception {
		change(notAgreedMemberId, "{\"agreed\": true}")
			.andExpect(status().isOk());

		List<MemberOutboxEvent> rows = memberOutboxEventRepository.findAll();
		assertThat(rows).singleElement().satisfies(row -> {
			assertThat(row.getTopic()).isEqualTo(KafkaTopics.MARKETING_CONSENT_CHANGED);
			assertThat(row.getRefId()).isEqualTo(notAgreedMemberId);
			MarketingConsentChangedEvent body =
				jsonConverter.fromJson(row.getPayload(), MarketingConsentChangedEvent.class);
			assertThat(body.eventId()).isEqualTo(row.getEventId());
			assertThat(body.memberId()).isEqualTo(notAgreedMemberId);
			assertThat(body.agreed()).isTrue();
			assertThat(body.changedAt()).isEqualTo(latestMarketing(notAgreedMemberId).getDecidedAt());
		});
	}

	@Test
	@DisplayName("지금과 같은 값으로 보내면 200이지만 동의 기록도 이벤트도 늘지 않고, 처음 동의 시각이 그대로다")
	void sameValueChangesNothing() throws Exception {
		TermsAgreement joined = latestMarketing(agreedMemberId);
		long before = countMarketing(agreedMemberId);

		ResultActions result = change(agreedMemberId, "{\"agreed\": true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.agreed").value(true));

		assertThat(decidedAtOf(result)).isEqualTo(joined.getDecidedAt());

		assertThat(countMarketing(agreedMemberId)).isEqualTo(before);
		assertThat(latestMarketing(agreedMemberId).getId()).isEqualTo(joined.getId());
		assertThat(memberOutboxEventRepository.count()).isZero();
	}

	@Test
	@DisplayName("끈 뒤 다시 켜면 마지막 기록(켬)이 지금 상태가 된다")
	void latestRowWins() throws Exception {
		change(agreedMemberId, "{\"agreed\": false}").andExpect(status().isOk());
		change(agreedMemberId, "{\"agreed\": true}").andExpect(status().isOk());

		find(agreedMemberId).andExpect(jsonPath("$.data.agreed").value(true));
		// 아웃박스는 쌓인 순서(ID)대로 보내므로, 끔 → 켬 순서로 남아야 받는 쪽도 마지막에 켬이 된다
		assertThat(memberOutboxEventRepository.findAll().stream()
			.sorted(Comparator.comparing(MemberOutboxEvent::getId))
			.map(row -> jsonConverter.fromJson(row.getPayload(), MarketingConsentChangedEvent.class).agreed())
			.toList()).containsExactly(false, true);
	}

	@Test
	@DisplayName("아웃박스 저장이 실패하면 동의 기록도 롤백되어, 동의는 바뀌었는데 이벤트가 없는 일이 생기지 않는다")
	void outboxFailureRollsBackAgreement() throws Exception {
		long before = countMarketing(agreedMemberId);
		willThrow(new IllegalStateException("아웃박스 저장 실패")).given(memberOutboxEventRepository).save(any());

		change(agreedMemberId, "{\"agreed\": false}")
			.andExpect(status().isInternalServerError());

		assertThat(countMarketing(agreedMemberId)).isEqualTo(before);
		assertThat(latestMarketing(agreedMemberId).isAgreed()).isTrue();
	}

	@Test
	@DisplayName("agreed가 없거나 참·거짓으로 읽을 수 없는 값(\"yes\")이면 400 INVALID_REQUEST이고 아무것도 바뀌지 않는다")
	void invalidBody() throws Exception {
		long before = countMarketing(agreedMemberId);

		change(agreedMemberId, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		change(agreedMemberId, "{\"agreed\": \"yes\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertThat(countMarketing(agreedMemberId)).isEqualTo(before);
		assertThat(memberOutboxEventRepository.count()).isZero();
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 조회·변경 모두 401 LOGIN_REQUIRED")
	void loginRequired() throws Exception {
		mockMvc.perform(get(CONSENT))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
		mockMvc.perform(put(CONSENT).contentType(MediaType.APPLICATION_JSON).content("{\"agreed\": false}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
	}

	@Test
	@DisplayName("없는 회원이나 탈퇴한 회원은 조회·변경 모두 404 MEMBER_NOT_FOUND이고 기록·이벤트가 남지 않는다")
	void withdrawnMemberNotFound() throws Exception {
		transactionTemplate.executeWithoutResult(status ->
			memberRepository.findById(agreedMemberId).orElseThrow().withdraw(LocalDateTime.now()));
		memberOutboxEventRepository.deleteAll();
		long before = countMarketing(agreedMemberId);

		find(agreedMemberId)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
		change(agreedMemberId, "{\"agreed\": false}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
		change(Long.MAX_VALUE, "{\"agreed\": false}")
			.andExpect(status().isNotFound());

		assertThat(countMarketing(agreedMemberId)).isEqualTo(before);
		assertThat(memberOutboxEventRepository.count()).isZero();
	}

	private ResultActions find(long memberId) throws Exception {
		return mockMvc.perform(get(CONSENT).header(MemberIdHeader.NAME, memberId));
	}

	private ResultActions change(long memberId, String body) throws Exception {
		return mockMvc.perform(put(CONSENT)
			.header(MemberIdHeader.NAME, memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	// 응답 JSON은 소수점 자릿수를 줄여 쓸 수 있어 문자열이 아니라 시각으로 읽어 비교한다
	private static LocalDateTime decidedAtOf(ResultActions result) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString();
		return LocalDateTime.parse(JsonPath.read(body, "$.data.decidedAt"));
	}

	private List<TermsAgreement> marketingRows(long memberId) {
		return termsAgreementRepository.findByMemberId(memberId).stream()
			.filter(agreement -> agreement.getTerms().getType() == TermsType.MARKETING)
			.toList();
	}

	private long countMarketing(long memberId) {
		return marketingRows(memberId).size();
	}

	// 지금 상태는 마케팅 약관의 마지막 기록(가장 늦게 쌓인 줄)이다 (BE-021)
	private TermsAgreement latestMarketing(long memberId) {
		return marketingRows(memberId).stream()
			.max(Comparator.comparing(TermsAgreement::getId))
			.orElseThrow();
	}
}
