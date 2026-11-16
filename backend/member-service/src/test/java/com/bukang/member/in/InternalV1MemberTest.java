package com.bukang.member.in;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.member.app.MemberFacade;
import com.bukang.member.app.SellerFacade;
import com.bukang.member.domain.Member;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.SellerRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.payoutaccount.InMemoryPayoutAccountVerifyLimiter;
import com.bukang.member.support.MemberFixture;

// 정산 서비스가 부르는 회원 내부 API: 회원 ID로 판매자 ID 찾기, 관리자 여부 조회
// 회원 ID와 판매자 ID가 같은 번호가 되지 않게, 판매자 신청을 한 번 더 만들어 번호를 어긋나게 둔다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({InMemoryIdentityVerificationStore.Config.class, InMemoryPayoutAccountVerifyLimiter.Config.class})
class InternalV1MemberTest {
	@Autowired
	private InMemoryIdentityVerificationStore identityStore;

	private static final String SELLER_OF_MEMBER = "/internal/v1/member/members/{memberId}/seller";
	private static final String ADMIN_OF_MEMBER = "/internal/v1/member/members/{memberId}/admin";
	private static final String PAYOUT_ACCOUNT = "/internal/v1/member/sellers/{sellerId}/payout-account";

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
	private JdbcTemplate jdbcTemplate;

	private long memberId;
	private long adminId;

	@BeforeEach
	void setUp() {
		memberId = memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
		adminId = memberFacade.join(MemberFixture.joinRequest()
			.email("admin@crewrun.com")
			.build(identityStore)).getId();
		memberFacade.grantAdmin("admin@crewrun.com");
	}

	@AfterEach
	void tearDown() {
		// 판매자·동의 기록이 회원을 참조하므로(FK) 먼저 지운다
		sellerRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("승인된 판매자면 회원 ID가 아니라 판매자 신청서 ID와 APPROVED를 돌려준다")
	void approvedSeller() throws Exception {
		// 다른 회원의 신청을 먼저 만들어, 이 회원의 판매자 ID가 회원 ID와 다른 번호가 되게 한다
		sellerFacade.apply(adminId, MemberFixture.sellerApplyRequest()).getId();
		long sellerId = sellerFacade.apply(memberId, MemberFixture.sellerApplyRequest()).getId();
		sellerFacade.approve(adminId, sellerId);

		findSellerOf(memberId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("회원의 판매자 신청을 조회했습니다."))
			.andExpect(jsonPath("$.data.sellerId").value(sellerId))
			.andExpect(jsonPath("$.data.status").value("APPROVED"));
	}

	@Test
	@DisplayName("대기 중인 신청이면 그 신청의 ID와 PENDING을 돌려준다")
	void pendingSeller() throws Exception {
		long sellerId = sellerFacade.apply(memberId, MemberFixture.sellerApplyRequest()).getId();

		findSellerOf(memberId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sellerId").value(sellerId))
			.andExpect(jsonPath("$.data.status").value("PENDING"));
	}

	@Test
	@DisplayName("반려 뒤 다시 신청하지 않았으면 반려된 신청과 REJECTED를 돌려준다")
	void rejectedSeller() throws Exception {
		long sellerId = sellerFacade.apply(memberId, MemberFixture.sellerApplyRequest()).getId();
		sellerFacade.reject(adminId, sellerId, "사업자등록번호 확인 불가");

		findSellerOf(memberId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sellerId").value(sellerId))
			.andExpect(jsonPath("$.data.status").value("REJECTED"));
	}

	@Test
	@DisplayName("반려 뒤 다시 신청해 승인되면 가장 최근(승인된) 신청을 돌려준다")
	void approvedAfterRejected() throws Exception {
		long rejectedId = sellerFacade.apply(memberId, MemberFixture.sellerApplyRequest()).getId();
		sellerFacade.reject(adminId, rejectedId, "사업자등록번호 확인 불가");
		long approvedId = sellerFacade.apply(memberId, MemberFixture.sellerApplyRequest()).getId();
		sellerFacade.approve(adminId, approvedId);

		findSellerOf(memberId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sellerId").value(approvedId))
			.andExpect(jsonPath("$.data.status").value("APPROVED"));
	}

	@Test
	@DisplayName("판매자 신청을 한 적이 없으면 200과 빈 칸을 돌려준다")
	void noSeller() throws Exception {
		findSellerOf(memberId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sellerId").isEmpty())
			.andExpect(jsonPath("$.data.status").isEmpty());
	}

	@Test
	@DisplayName("탈퇴한 회원은 승인된 판매자였어도 404다")
	void withdrawnMember() throws Exception {
		long sellerId = sellerFacade.apply(memberId, MemberFixture.sellerApplyRequest()).getId();
		sellerFacade.approve(adminId, sellerId);
		Member member = memberRepository.findById(memberId).orElseThrow();
		member.withdraw(LocalDateTime.now());
		memberRepository.save(member);

		findSellerOf(memberId)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
			.andExpect(jsonPath("$.message").value("존재하지 않는 회원입니다."));
	}

	@Test
	@DisplayName("없는 회원이면 404다")
	void unknownMember() throws Exception {
		findSellerOf(memberId + 1000)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	// 관리자 여부 조회 (정산 서비스가 관리자 API를 받을 때 부른다, PRO-265)
	@Test
	@DisplayName("관리자면 admin=true를 돌려준다")
	void admin() throws Exception {
		mockMvc.perform(get(ADMIN_OF_MEMBER, adminId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("관리자 여부를 조회했습니다."))
			.andExpect(jsonPath("$.data.admin").value(true));
	}

	@Test
	@DisplayName("일반 회원이면 403이 아니라 200 admin=false를 돌려준다")
	void notAdmin() throws Exception {
		mockMvc.perform(get(ADMIN_OF_MEMBER, memberId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.admin").value(false));
	}

	@Test
	@DisplayName("탈퇴한 관리자는 없는 회원으로 보고 404 MEMBER_NOT_FOUND를 돌려준다")
	void withdrawnAdminIsNotFound() throws Exception {
		Member admin = memberRepository.findById(adminId).orElseThrow();
		admin.withdraw(LocalDateTime.now());
		memberRepository.saveAndFlush(admin);

		mockMvc.perform(get(ADMIN_OF_MEMBER, adminId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	@Test
	@DisplayName("없는 회원의 관리자 여부를 물으면 404 MEMBER_NOT_FOUND를 돌려준다")
	void unknownMemberAdminIsNotFound() throws Exception {
		mockMvc.perform(get(ADMIN_OF_MEMBER, memberId + 1000))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	@Test
	@DisplayName("계좌를 확인하고 신청한 판매자는 정산 계좌 등록 여부가 true다")
	void payoutAccountRegistered() throws Exception {
		long sellerId = sellerFacade.apply(memberId, MemberFixture.sellerApplyRequest()).getId();

		mockMvc.perform(get(PAYOUT_ACCOUNT, sellerId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("판매자 정산 계좌 등록 여부를 조회했습니다."))
			.andExpect(jsonPath("$.data.registered").value(true));
	}

	@Test
	@DisplayName("계좌 없이 신청된 판매자(이 기능 전에 신청한 판매자)는 정산 계좌 등록 여부가 false다")
	void payoutAccountNotRegistered() throws Exception {
		long sellerId = sellerFacade.apply(memberId, MemberFixture.sellerApplyRequest()).getId();
		jdbcTemplate.update("update member_seller set payout_bank_code = null, payout_account_number = null, "
			+ "payout_account_holder_name = null, payout_account_holder_type = null, payout_account_verified_at = null "
			+ "where seller_id = ?", sellerId);

		mockMvc.perform(get(PAYOUT_ACCOUNT, sellerId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.registered").value(false));
	}

	@Test
	@DisplayName("없는 판매자 신청서의 정산 계좌를 물으면 404 SELLER_NOT_FOUND다")
	void payoutAccountOfUnknownSeller() throws Exception {
		mockMvc.perform(get(PAYOUT_ACCOUNT, 99999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SELLER_NOT_FOUND"));
	}

	private ResultActions findSellerOf(long id) throws Exception {
		return mockMvc.perform(get(SELLER_OF_MEMBER, id));
	}
}
