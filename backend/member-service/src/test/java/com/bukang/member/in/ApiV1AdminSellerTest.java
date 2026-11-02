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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

// 관리자의 판매자 신청 목록·승인·반려 (BE-020). 관리자는 회원 역할 칸(ADMIN)으로 알아본다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class ApiV1AdminSellerTest {
	private static final String ADMIN_SELLERS = "/api/v1/member/admin/sellers";

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

	private long adminId;
	private long applicantId;
	private long sellerId;

	@BeforeEach
	void setUp() {
		adminId = join("admin@crewrun.com", "관리자", "010-1234-5670");
		memberFacade.grantAdmin("admin@crewrun.com");
		applicantId = join("runner1@crewrun.com", "러너1", "010-1234-5671");
		sellerId = applyDirectly(applicantId);
		// 신청 때 발행한 이벤트는 세지 않는다
		applicationEvents.clear();
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
	@DisplayName("관리자가 아니면 모든 관리자 API가 403이다")
	void nonAdminIsForbidden() throws Exception {
		List<MockHttpServletRequestBuilder> requests = List.of(
			get(ADMIN_SELLERS),
			post(ADMIN_SELLERS + "/" + sellerId + "/approval"),
			post(ADMIN_SELLERS + "/" + sellerId + "/rejection")
				.contentType(MediaType.APPLICATION_JSON)
				.content(rejectBody("서류 확인 불가"))
		);

		for (MockHttpServletRequestBuilder request : requests) {
			mockMvc.perform(request.header(MemberIdHeader.NAME, applicantId))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("MEMBER_NOT_ADMIN"))
				.andExpect(jsonPath("$.message").value("관리자만 이용할 수 있습니다."));
		}
		assertThat(sellerRepository.findById(sellerId).orElseThrow().getStatus()).isEqualTo(SellerStatus.PENDING);
	}

	@Test
	@DisplayName("상태로 거르면 그 상태 신청만, 비우면 전체를 최근 신청부터 돌려준다")
	void findAllFiltersByStatus() throws Exception {
		long otherApplicantId = join("runner2@crewrun.com", "러너2", "010-1234-5672");
		long approvedId = applyDirectly(otherApplicantId);
		sellerFacade.approve(adminId, approvedId);

		mockMvc.perform(get(ADMIN_SELLERS).param("status", "PENDING").header(MemberIdHeader.NAME, adminId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("판매자 신청 목록을 조회했습니다."))
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].id").value(sellerId))
			.andExpect(jsonPath("$.data[0].status").value("PENDING"));

		mockMvc.perform(get(ADMIN_SELLERS).header(MemberIdHeader.NAME, adminId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].id").value(approvedId))
			.andExpect(jsonPath("$.data[1].id").value(sellerId));
	}

	@Test
	@DisplayName("승인하면 승인 상태·심사 시각이 채워지고 판매자 변경 이벤트를 발행한다")
	void approveSucceeds() throws Exception {
		review(sellerId, "approval", null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("판매자 신청을 승인했습니다."))
			.andExpect(jsonPath("$.data.id").value(sellerId))
			.andExpect(jsonPath("$.data.status").value("APPROVED"))
			.andExpect(jsonPath("$.data.reviewedAt").isNotEmpty())
			.andExpect(jsonPath("$.data.rejectReason").isEmpty());

		Seller saved = sellerRepository.findById(sellerId).orElseThrow();
		assertThat(saved.getStatus()).isEqualTo(SellerStatus.APPROVED);
		assertThat(saved.getReviewedAt()).isNotNull();
		assertThat(saved.getReviewedBy()).isEqualTo(adminId);

		List<SellerChangedEvent> events = applicationEvents.stream(SellerChangedEvent.class).toList();
		assertThat(events).hasSize(1);
		assertThat(events.getFirst().id()).isEqualTo(sellerId);
		assertThat(events.getFirst().status()).isEqualTo(SellerStatus.APPROVED);
		assertThat(events.getFirst().reviewedAt()).isNotNull();
	}

	@Test
	@DisplayName("사유와 함께 반려하면 반려 상태가 되고, 이벤트에는 사유가 없다")
	void rejectSucceeds() throws Exception {
		review(sellerId, "rejection", "서류 확인 불가")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("판매자 신청을 반려했습니다."))
			.andExpect(jsonPath("$.data.status").value("REJECTED"))
			.andExpect(jsonPath("$.data.reviewedAt").isNotEmpty())
			.andExpect(jsonPath("$.data.rejectReason").value("서류 확인 불가"));

		Seller saved = sellerRepository.findById(sellerId).orElseThrow();
		assertThat(saved.getStatus()).isEqualTo(SellerStatus.REJECTED);
		assertThat(saved.getRejectReason()).isEqualTo("서류 확인 불가");

		List<SellerChangedEvent> events = applicationEvents.stream(SellerChangedEvent.class).toList();
		assertThat(events).hasSize(1);
		assertThat(events.getFirst().status()).isEqualTo(SellerStatus.REJECTED);
	}

	@Test
	@DisplayName("반려 사유가 없거나 500자를 넘으면 400이다")
	void rejectFailsWithInvalidReason() throws Exception {
		review(sellerId, "rejection", " ")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("반려 사유를 작성해주세요."));

		review(sellerId, "rejection", "가".repeat(501))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("반려 사유는 500자 이하로 작성해주세요."));

		mockMvc.perform(post(ADMIN_SELLERS + "/" + sellerId + "/rejection")
				.header(MemberIdHeader.NAME, adminId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("반려 사유를 작성해주세요."));

		assertThat(sellerRepository.findById(sellerId).orElseThrow().getStatus()).isEqualTo(SellerStatus.PENDING);
	}

	@Test
	@DisplayName("이미 심사한 신청을 다시 승인·반려하면 409다")
	void reviewFailsWhenAlreadyReviewed() throws Exception {
		sellerFacade.approve(adminId, sellerId);
		applicationEvents.clear();

		review(sellerId, "approval", null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SELLER_ALREADY_REVIEWED"));
		review(sellerId, "rejection", "서류 확인 불가")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SELLER_ALREADY_REVIEWED"));

		assertThat(sellerRepository.findById(sellerId).orElseThrow().getStatus()).isEqualTo(SellerStatus.APPROVED);
		assertThat(applicationEvents.stream(SellerChangedEvent.class)).isEmpty();
	}

	@Test
	@DisplayName("없는 신청을 승인·반려하면 404다")
	void reviewFailsWithUnknownSeller() throws Exception {
		review(sellerId + 1000, "approval", null)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SELLER_NOT_FOUND"));
		review(sellerId + 1000, "rejection", "서류 확인 불가")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SELLER_NOT_FOUND"));
	}

	private long join(String email, String nickname, String phone) {
		return memberFacade.join(new MemberJoinRequestDto(
			email, "password1234", "김러너", nickname, phone, LocalDate.of(1995, 4, 12), null,
			List.of(TermsType.SERVICE, TermsType.PRIVACY)
		)).getId();
	}

	private long applyDirectly(long memberId) {
		return sellerFacade.apply(memberId,
			new SellerApplyRequestDto(SellerType.BUSINESS, "크루런 스포츠", "123-45-67890", "김러너")).getId();
	}

	// reason이 null이면 본문 없이 보낸다 (승인)
	@Test
	@DisplayName("탈퇴한 관리자가 남은 토큰으로 관리자 API를 부르면 404 MEMBER_NOT_FOUND이고 심사되지 않는다")
	void withdrawnAdminCannotReview() throws Exception {
		Member admin = memberRepository.findById(adminId).orElseThrow();
		admin.withdraw(LocalDateTime.now());
		memberRepository.saveAndFlush(admin);

		mockMvc.perform(post(ADMIN_SELLERS + "/" + sellerId + "/approval").header(MemberIdHeader.NAME, adminId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
		assertThat(sellerRepository.findById(sellerId).orElseThrow().getStatus()).isEqualTo(SellerStatus.PENDING);
	}

	private ResultActions review(long id, String action, String reason) throws Exception {
		MockHttpServletRequestBuilder request = post(ADMIN_SELLERS + "/" + id + "/" + action)
			.header(MemberIdHeader.NAME, adminId);
		if (reason != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(rejectBody(reason));
		}
		return mockMvc.perform(request);
	}

	private String rejectBody(String reason) {
		return """
			{ "reason": "%s" }
			""".formatted(reason);
	}
}
