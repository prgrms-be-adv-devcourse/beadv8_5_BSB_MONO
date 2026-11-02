package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.common.shared.seller.event.SellerChangedEvent;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.app.SellerFacade;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.Seller;
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.MemberJoinRequestDto;
import com.bukang.member.dto.SellerApplyRequestDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.SellerRepository;
import com.bukang.member.out.TermsAgreementRepository;

// 판매자 등록 신청과 내 신청 조회 (BE-020). X-Member-Id 헤더는 테스트에서 직접 넣는다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class ApiV1SellerTest {
	private static final String SELLERS = "/api/v1/member/sellers";
	private static final String MY_SELLER = "/api/v1/member/sellers/me";
	private static final String RESULT_CHECK = "/api/v1/member/sellers/me/result-check";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private MemberFacade memberFacade;
	@Autowired
	private SellerFacade sellerFacade;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private SellerRepository sellerRepository;

	@Autowired
	private TermsAgreementRepository termsAgreementRepository;
	@Autowired
	private ApplicationEvents applicationEvents;

	private long memberId;
	private long adminId;

	@BeforeEach
	void setUp() {
		memberId = join("runner1@crewrun.com", "러너1", "010-1234-5678");
		adminId = join("admin@crewrun.com", "관리자", "010-1234-5679");
		memberFacade.grantAdmin("admin@crewrun.com");
	}

	@AfterEach
	void tearDown() {
		// 판매자가 회원을 참조하므로 먼저 지운다
		sellerRepository.deleteAll();
		// 동의 기록도 회원을 참조하므로(FK) 먼저 지운다
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("사업자로 신청하면 201과 대기 상태 신청을 받고, 판매자 변경 이벤트를 발행한다")
	void applyBusinessSucceeds() throws Exception {
		apply(memberId, "BUSINESS", "크루런 스포츠", "1234567890", "김러너")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value(201))
			.andExpect(jsonPath("$.message").value("판매자 등록을 신청했습니다."))
			.andExpect(jsonPath("$.data.id").isNumber())
			.andExpect(jsonPath("$.data.memberId").value(memberId))
			.andExpect(jsonPath("$.data.sellerType").value("BUSINESS"))
			.andExpect(jsonPath("$.data.businessName").value("크루런 스포츠"))
			// 하이픈 없이 보내도 123-45-67890 모양으로 저장한다
			.andExpect(jsonPath("$.data.businessNumber").value("123-45-67890"))
			.andExpect(jsonPath("$.data.representativeName").value("김러너"))
			.andExpect(jsonPath("$.data.status").value("PENDING"))
			.andExpect(jsonPath("$.data.requestedAt").isNotEmpty())
			.andExpect(jsonPath("$.data.reviewedAt").isEmpty())
			.andExpect(jsonPath("$.data.rejectReason").isEmpty());

		Seller saved = sellerRepository.findFirstByMemberIdOrderByIdDesc(memberId).orElseThrow();
		List<SellerChangedEvent> events = applicationEvents.stream(SellerChangedEvent.class).toList();
		assertThat(events).hasSize(1);
		SellerChangedEvent event = events.getFirst();
		assertThat(event.id()).isEqualTo(saved.getId()).isNotZero();
		assertThat(event.memberId()).isEqualTo(memberId);
		assertThat(event.status()).isEqualTo(SellerStatus.PENDING);
		assertThat(event.businessNumber()).isEqualTo("123-45-67890");
		assertThat(event.requestedAt()).isNotNull();
		assertThat(event.reviewedAt()).isNull();
	}

	@Test
	@DisplayName("비사업자로 신청하면 보낸 상호·사업자등록번호를 버리고 201이다")
	void applyIndividualDropsBusinessFields() throws Exception {
		apply(memberId, "INDIVIDUAL", "크루런 스포츠", "123-45-67890", "김러너")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.sellerType").value("INDIVIDUAL"))
			.andExpect(jsonPath("$.data.businessName").isEmpty())
			.andExpect(jsonPath("$.data.businessNumber").isEmpty())
			.andExpect(jsonPath("$.data.representativeName").value("김러너"));

		Seller saved = sellerRepository.findFirstByMemberIdOrderByIdDesc(memberId).orElseThrow();
		assertThat(saved.getBusinessName()).isNull();
		assertThat(saved.getBusinessNumber()).isNull();
	}

	@Test
	@DisplayName("비사업자는 상호·사업자등록번호 없이 신청할 수 있다")
	void applyIndividualWithoutBusinessFields() throws Exception {
		apply(memberId, "INDIVIDUAL", null, null, "김러너")
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("사업자인데 사업자등록번호가 없으면 400이다")
	void applyBusinessFailsWithoutBusinessNumber() throws Exception {
		apply(memberId, "BUSINESS", "크루런 스포츠", null, "김러너")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("사업자등록번호를 작성해주세요."));
	}

	@Test
	@DisplayName("사업자인데 상호가 없으면 400이다")
	void applyBusinessFailsWithoutBusinessName() throws Exception {
		apply(memberId, "BUSINESS", " ", "123-45-67890", "김러너")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("상호를 작성해주세요."));
	}

	@Test
	@DisplayName("사업자등록번호가 숫자 10자리 형식이 아니면 400이다")
	void applyFailsWithInvalidBusinessNumber() throws Exception {
		apply(memberId, "BUSINESS", "크루런 스포츠", "123-45-6789", "김러너")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("사업자등록번호는 숫자 10자리로 작성해주세요."));
	}

	@Test
	@DisplayName("판매자 종류가 없으면 400이다")
	void applyFailsWithoutSellerType() throws Exception {
		apply(memberId, null, null, null, "김러너")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("판매자 종류를 선택해주세요."));
	}

	@Test
	@DisplayName("대표자 이름이 50자를 넘으면 400이다")
	void applyFailsWithTooLongRepresentativeName() throws Exception {
		apply(memberId, "INDIVIDUAL", null, null, "가".repeat(51))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("대표자 이름은 50자 이하로 작성해주세요."));
	}

	@Test
	@DisplayName("대기 중인 신청이 있는데 또 신청하면 409다")
	void applyFailsWhilePending() throws Exception {
		applyDirectly(memberId);

		apply(memberId, "INDIVIDUAL", null, null, "김러너")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SELLER_ALREADY_APPLIED"));
	}

	@Test
	@DisplayName("승인된 신청이 있는데 또 신청하면 409다")
	void applyFailsWhenApproved() throws Exception {
		long sellerId = applyDirectly(memberId);
		sellerFacade.approve(adminId, sellerId);

		apply(memberId, "INDIVIDUAL", null, null, "김러너")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SELLER_ALREADY_APPLIED"));
	}

	@Test
	@DisplayName("반려된 뒤 다시 신청하면 201이고 새 행이 생긴다")
	void applyAgainAfterRejected() throws Exception {
		long rejectedId = applyDirectly(memberId);
		sellerFacade.reject(adminId, rejectedId, "서류 확인 불가");

		apply(memberId, "INDIVIDUAL", null, null, "김러너")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("PENDING"));

		assertThat(sellerRepository.count()).isEqualTo(2);
		assertThat(sellerRepository.findById(rejectedId).orElseThrow().getStatus()).isEqualTo(SellerStatus.REJECTED);
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 401이다")
	void applyFailsWithoutHeader() throws Exception {
		mockMvc.perform(post(SELLERS)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("INDIVIDUAL", null, null, "김러너")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("내 신청 조회는 가장 최근 신청을 반려 사유와 함께 돌려준다")
	void findMineReturnsLatestWithRejectReason() throws Exception {
		long firstId = applyDirectly(memberId);
		sellerFacade.reject(adminId, firstId, "서류 확인 불가");
		long secondId = applyDirectly(memberId);
		sellerFacade.reject(adminId, secondId, "대표자 이름 불일치");

		mockMvc.perform(get(MY_SELLER).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("판매자 신청 내역을 조회했습니다."))
			.andExpect(jsonPath("$.data.id").value(secondId))
			.andExpect(jsonPath("$.data.status").value("REJECTED"))
			.andExpect(jsonPath("$.data.reviewedAt").isNotEmpty())
			.andExpect(jsonPath("$.data.rejectReason").value("대표자 이름 불일치"));
	}

	@Test
	@DisplayName("신청한 적이 없으면 내 신청 조회는 404다")
	void findMineFailsWithoutApplication() throws Exception {
		mockMvc.perform(get(MY_SELLER).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SELLER_NOT_FOUND"));
	}

	@Test
	@DisplayName("승인된 신청의 결과를 확인하면 200과 확인 시각을 받고, 내 신청 조회에도 나온다")
	void checkResultRecordsTime() throws Exception {
		long sellerId = applyDirectly(memberId);
		sellerFacade.approve(adminId, sellerId);

		mockMvc.perform(get(MY_SELLER).header(MemberIdHeader.NAME, memberId))
			.andExpect(jsonPath("$.data.resultCheckedAt").isEmpty());

		mockMvc.perform(post(RESULT_CHECK).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("판매자 신청 결과를 확인했습니다."))
			.andExpect(jsonPath("$.data.id").value(sellerId))
			.andExpect(jsonPath("$.data.status").value("APPROVED"))
			.andExpect(jsonPath("$.data.resultCheckedAt").isNotEmpty());

		LocalDateTime saved = sellerRepository.findById(sellerId).orElseThrow().getResultCheckedAt();
		assertThat(saved).isNotNull();
		mockMvc.perform(get(MY_SELLER).header(MemberIdHeader.NAME, memberId))
			.andExpect(jsonPath("$.data.resultCheckedAt").isNotEmpty());
	}

	@Test
	@DisplayName("결과 확인을 다시 보내도 200이고 처음 시각을 그대로 둔다")
	void checkResultTwiceKeepsFirstTime() throws Exception {
		long sellerId = applyDirectly(memberId);
		sellerFacade.reject(adminId, sellerId, "서류 확인 불가");
		mockMvc.perform(post(RESULT_CHECK).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isOk());
		LocalDateTime first = sellerRepository.findById(sellerId).orElseThrow().getResultCheckedAt();

		mockMvc.perform(post(RESULT_CHECK).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("REJECTED"));

		assertThat(sellerRepository.findById(sellerId).orElseThrow().getResultCheckedAt()).isEqualTo(first);
	}

	@Test
	@DisplayName("심사 중인 신청의 결과 확인은 409 SELLER_NOT_REVIEWED이고 기록하지 않는다")
	void checkResultFailsWhilePending() throws Exception {
		long sellerId = applyDirectly(memberId);

		mockMvc.perform(post(RESULT_CHECK).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SELLER_NOT_REVIEWED"))
			.andExpect(jsonPath("$.message").value("아직 심사 중인 판매자 신청입니다."));

		assertThat(sellerRepository.findById(sellerId).orElseThrow().getResultCheckedAt()).isNull();
	}

	@Test
	@DisplayName("반려 뒤 다시 신청하면 결과 확인은 새 신청서 기준이라 다시 비어 있다")
	void reapplyStartsWithEmptyResultCheck() throws Exception {
		long rejectedId = applyDirectly(memberId);
		sellerFacade.reject(adminId, rejectedId, "서류 확인 불가");
		mockMvc.perform(post(RESULT_CHECK).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isOk());
		long secondId = applyDirectly(memberId);

		mockMvc.perform(get(MY_SELLER).header(MemberIdHeader.NAME, memberId))
			.andExpect(jsonPath("$.data.id").value(secondId))
			.andExpect(jsonPath("$.data.resultCheckedAt").isEmpty());
		assertThat(sellerRepository.findById(rejectedId).orElseThrow().getResultCheckedAt()).isNotNull();
	}

	@Test
	@DisplayName("신청한 적이 없으면 결과 확인은 404 SELLER_NOT_FOUND다")
	void checkResultFailsWithoutApplication() throws Exception {
		mockMvc.perform(post(RESULT_CHECK).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SELLER_NOT_FOUND"))
			.andExpect(jsonPath("$.message").value("판매자 신청 내역이 없습니다."));
	}

	@Test
	@DisplayName("회원 ID 헤더 없이 결과 확인을 보내면 401이다")
	void checkResultFailsWithoutHeader() throws Exception {
		mockMvc.perform(post(RESULT_CHECK))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("탈퇴한 회원이 남은 토큰으로 판매자 신청하면 404 MEMBER_NOT_FOUND이고 신청이 생기지 않는다")
	void withdrawnMemberCannotApply() throws Exception {
		markWithdrawn(memberId);

		apply(memberId, "BUSINESS", "크루런 스포츠", "1234567890", "김러너")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
		assertThat(sellerRepository.count()).isZero();
	}

	// 탈퇴 API는 cash·order 확인이 필요해, 엔티티의 탈퇴 처리만 직접 한다
	private void markWithdrawn(long id) {
		Member member = memberRepository.findById(id).orElseThrow();
		member.withdraw(LocalDateTime.now());
		memberRepository.saveAndFlush(member);
	}

	private long join(String email, String nickname, String phone) {
		return memberFacade.join(new MemberJoinRequestDto(
			email, "password1234", "김러너", nickname, phone, LocalDate.of(1995, 4, 12), null,
			List.of(TermsType.SERVICE, TermsType.PRIVACY)
		)).getId();
	}

	private long applyDirectly(long applicantId) {
		return sellerFacade.apply(applicantId,
			new SellerApplyRequestDto(SellerType.BUSINESS, "크루런 스포츠", "123-45-67890", "김러너")).getId();
	}

	private ResultActions apply(long id, String sellerType, String businessName, String businessNumber,
		String representativeName) throws Exception {
		return mockMvc.perform(post(SELLERS)
			.header(MemberIdHeader.NAME, id)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(sellerType, businessName, businessNumber, representativeName)));
	}

	private String body(String sellerType, String businessName, String businessNumber, String representativeName) {
		return """
			{
				"sellerType": %s,
				"businessName": %s,
				"businessNumber": %s,
				"representativeName": %s
			}
			""".formatted(json(sellerType), json(businessName), json(businessNumber), json(representativeName));
	}

	private String json(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}
}
