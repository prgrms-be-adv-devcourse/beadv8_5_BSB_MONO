package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.app.SellerFacade;
import com.bukang.member.domain.PayoutAccountHolderType;
import com.bukang.member.domain.Seller;
import com.bukang.member.domain.exception.SellerAccountNotVerifiedException;
import com.bukang.member.domain.exception.SellerAccountVerifyUnavailableException;
import com.bukang.member.dto.SellerApplyRequestDto;
import com.bukang.member.dto.SellerPayoutAccountModifyRequestDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.SellerRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.payoutaccount.InMemoryPayoutAccountVerifyLimiter;
import com.bukang.member.out.payoutaccount.PayoutAccountInquiry;
import com.bukang.member.out.payoutaccount.StubPayoutAccountVerifier;
import com.bukang.member.support.MemberFixture;

// 판매자가 자기 정산 계좌를 조회하고 직접 등록·수정한다 (CRU-80). 오픈뱅킹 대신 StubPayoutAccountVerifier가 답한다
// 가입한 회원의 본인인증 이름은 김러너, 생년월일은 1995-04-12다. 처음 계좌는 004 은행 1101230000678(끝 0678)이다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@Import({InMemoryIdentityVerificationStore.Config.class, InMemoryPayoutAccountVerifyLimiter.Config.class,
	StubPayoutAccountVerifier.Config.class})
class ApiV1SellerMyPayoutAccountTest {
	private static final String MY_ACCOUNT = "/api/v1/member/sellers/me/payout-account";
	private static final String OLD_NUMBER = "1101230000678";
	private static final String NEW_NUMBER = "3332000100123";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private InMemoryIdentityVerificationStore identityStore;
	@Autowired
	private InMemoryPayoutAccountVerifyLimiter limiter;
	@Autowired
	private StubPayoutAccountVerifier verifier;
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
	private long sellerId;

	@BeforeEach
	void setUp() {
		verifier.reset();
		limiter.clear();
		memberId = memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
		adminId = memberFacade.join(MemberFixture.joinRequest().email("admin@crewrun.com").build(identityStore))
			.getId();
		memberFacade.grantAdmin("admin@crewrun.com");
		// 신청 때 계좌 확인이 한 번 일어나므로 시작 전에 기록을 비운다
		sellerId = sellerFacade.apply(memberId, MemberFixture.sellerApplyRequest()).getId();
		verifier.reset();
		limiter.clear();
	}

	@AfterEach
	void tearDown() {
		sellerRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	private void approve() {
		sellerFacade.approve(adminId, sellerId);
	}

	@Test
	@DisplayName("승인된 판매자는 은행·예금주·계좌번호 끝 4자리만 조회하고, 전체 계좌번호는 응답에 없다")
	void findShowsOnlyLastFour() throws Exception {
		approve();

		mockMvc.perform(get(MY_ACCOUNT).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.registered").value(true))
			.andExpect(jsonPath("$.data.bankCode").value("004"))
			.andExpect(jsonPath("$.data.accountHolderName").value("김러너"))
			.andExpect(jsonPath("$.data.accountNumberLast4").value("0678"))
			.andExpect(content().string(not(containsString(OLD_NUMBER))));
	}

	@Test
	@DisplayName("계좌가 없는 승인된 판매자는 registered가 false이고 나머지 칸은 비어 있다")
	void findWithoutAccount() throws Exception {
		approve();
		jdbcTemplate.update("update member_seller set payout_bank_code = null, payout_account_number = null, "
			+ "payout_account_holder_name = null, payout_account_holder_type = null, "
			+ "payout_account_verified_at = null where seller_id = ?", sellerId);

		mockMvc.perform(get(MY_ACCOUNT).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.registered").value(false))
			.andExpect(jsonPath("$.data.bankCode").doesNotExist())
			.andExpect(jsonPath("$.data.accountNumberLast4").doesNotExist());
	}

	@Test
	@DisplayName("승인되지 않은 판매자(심사 중·반려)는 조회도 수정도 403이고 은행을 부르지 않는다")
	void notApprovedIsForbidden() throws Exception {
		mockMvc.perform(get(MY_ACCOUNT).header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("SELLER_NOT_APPROVED"));
		modify(memberId, "088", NEW_NUMBER, "PERSONAL")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("SELLER_NOT_APPROVED"));

		sellerFacade.reject(adminId, sellerId, "서류 확인 불가");
		modify(memberId, "088", NEW_NUMBER, "PERSONAL")
			.andExpect(status().isForbidden());

		assertThat(verifier.inquiries()).isEmpty();
		assertThat(sellerRepository.findById(sellerId).orElseThrow().getPayoutBankCode()).isEqualTo("004");
	}

	@Test
	@DisplayName("판매자 신청이 없는 회원은 조회도 수정도 404다")
	void noSellerIsNotFound() throws Exception {
		long otherId = memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();

		mockMvc.perform(get(MY_ACCOUNT).header(MemberIdHeader.NAME, otherId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SELLER_NOT_FOUND"));
		modify(otherId, "088", NEW_NUMBER, "PERSONAL")
			.andExpect(status().isNotFound());

		assertThat(verifier.inquiries()).isEmpty();
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 조회도 수정도 401이다")
	void loginRequired() throws Exception {
		mockMvc.perform(get(MY_ACCOUNT)).andExpect(status().isUnauthorized());
		mockMvc.perform(put(MY_ACCOUNT).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("다른 회원이 요청해도 자기 판매자 정보만 다룬다. 남의 계좌는 바뀌지 않는다")
	void cannotTouchOthersAccount() throws Exception {
		approve();
		long otherId = memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();

		modify(otherId, "088", NEW_NUMBER, "PERSONAL").andExpect(status().isNotFound());

		Seller mine = sellerRepository.findById(sellerId).orElseThrow();
		assertThat(mine.getPayoutBankCode()).isEqualTo("004");
		assertThat(mine.getPayoutAccountNumber()).isEqualTo(OLD_NUMBER);
	}

	@Test
	@DisplayName("승인된 판매자가 예금주 확인을 통과하면 계좌를 바꾸고, 암호화해 저장하며, 응답과 로그에 전체 번호가 없다")
	void modifySaves(CapturedOutput output) throws Exception {
		approve();
		Seller before = sellerRepository.findById(sellerId).orElseThrow();

		modify(memberId, "088", NEW_NUMBER, "PERSONAL")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.registered").value(true))
			.andExpect(jsonPath("$.data.bankCode").value("088"))
			.andExpect(jsonPath("$.data.accountHolderName").value("김러너"))
			.andExpect(jsonPath("$.data.accountNumberLast4").value("0123"))
			.andExpect(content().string(not(containsString(NEW_NUMBER))));

		PayoutAccountInquiry inquiry = verifier.inquiries().getFirst();
		assertThat(inquiry.bankCode()).isEqualTo("088");
		assertThat(inquiry.accountNumber()).isEqualTo(NEW_NUMBER);
		assertThat(inquiry.holderInfo()).isEqualTo("950412");

		Seller after = sellerRepository.findById(sellerId).orElseThrow();
		assertThat(after.getPayoutBankCode()).isEqualTo("088");
		assertThat(after.getPayoutAccountNumber()).isEqualTo(NEW_NUMBER);
		assertThat(after.getPayoutAccountVerifiedAt()).isAfterOrEqualTo(before.getPayoutAccountVerifiedAt());
		String stored = jdbcTemplate.queryForObject(
			"select payout_account_number from member_seller where seller_id = ?", String.class, sellerId);
		assertThat(stored).isNotBlank().doesNotContain(NEW_NUMBER);
		assertThat(output).doesNotContain(NEW_NUMBER);
	}

	@Test
	@DisplayName("계좌가 없던 승인된 판매자도 등록할 수 있다")
	void registerWhenEmpty() throws Exception {
		approve();
		jdbcTemplate.update("update member_seller set payout_bank_code = null, payout_account_number = null, "
			+ "payout_account_holder_name = null, payout_account_holder_type = null, "
			+ "payout_account_verified_at = null where seller_id = ?", sellerId);

		modify(memberId, "088", NEW_NUMBER, "PERSONAL").andExpect(status().isOk());

		assertThat(sellerFacade.findPayoutAccount(sellerId).registered()).isTrue();
	}

	@Test
	@DisplayName("예금주 이름이 다르거나 은행이 거절하면 400이고 기존 계좌를 그대로 둔다")
	void verifyFailureKeepsOldAccount() throws Exception {
		approve();
		verifier.answer(inquiry -> "박러너");
		modify(memberId, "088", NEW_NUMBER, "PERSONAL")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SELLER_ACCOUNT_NOT_VERIFIED"));

		verifier.answer(inquiry -> {
			throw new SellerAccountNotVerifiedException();
		});
		modify(memberId, "088", NEW_NUMBER, "PERSONAL").andExpect(status().isBadRequest());

		assertOldAccountKept();
	}

	@Test
	@DisplayName("오픈뱅킹이 응답하지 않으면 503이고 기존 계좌를 그대로 두며, 하루 횟수를 쓰지 않는다")
	void unavailableKeepsOldAccount() throws Exception {
		approve();
		verifier.answer(inquiry -> {
			throw new SellerAccountVerifyUnavailableException();
		});
		for (int i = 0; i < 6; i++) {
			modify(memberId, "088", NEW_NUMBER, "PERSONAL")
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("SELLER_ACCOUNT_VERIFY_UNAVAILABLE"));
		}

		assertOldAccountKept();
	}

	@Test
	@DisplayName("하루 다섯 번 확인한 뒤 여섯 번째는 오픈뱅킹을 부르기 전에 429로 막는다")
	void dailyLimit() throws Exception {
		approve();
		verifier.answer(inquiry -> "박러너");
		for (int i = 0; i < 5; i++) {
			modify(memberId, "088", NEW_NUMBER, "PERSONAL").andExpect(status().isBadRequest());
		}

		modify(memberId, "088", NEW_NUMBER, "PERSONAL")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("SELLER_ACCOUNT_VERIFY_TOO_MANY_REQUESTS"));

		assertThat(verifier.inquiries()).hasSize(5);
	}

	@Test
	@DisplayName("본인 명의는 본인인증 해시가 없으면 은행을 부르지 않고 400으로 다시 인증하라고 한다")
	void personalNeedsReverify() throws Exception {
		approve();
		jdbcTemplate.update("update member_member set name_birth_hash = null where member_id = ?", memberId);

		modify(memberId, "088", NEW_NUMBER, "PERSONAL")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SELLER_IDENTITY_REVERIFY_REQUIRED"));

		assertThat(verifier.inquiries()).isEmpty();
		assertOldAccountKept();
	}

	@Test
	@DisplayName("사업자 판매자의 사업자 명의 계좌는 저장된 사업자등록번호 숫자로 조회하고 이름 비교 없이 통과한다")
	void businessHolderUsesSavedBusinessNumber() throws Exception {
		approve();
		verifier.answer(inquiry -> "(주)크루런스포츠");

		modify(memberId, "088", NEW_NUMBER, "BUSINESS").andExpect(status().isOk());

		PayoutAccountInquiry inquiry = verifier.inquiries().getFirst();
		assertThat(inquiry.holderType()).isEqualTo(PayoutAccountHolderType.BUSINESS);
		assertThat(inquiry.holderInfo()).isEqualTo("1234567890");
		assertThat(sellerRepository.findById(sellerId).orElseThrow().getPayoutAccountHolderType())
			.isEqualTo(PayoutAccountHolderType.BUSINESS);
	}

	@Test
	@DisplayName("비사업자 판매자는 사업자 명의 계좌를 고를 수 없어 400이고 은행을 부르지 않는다")
	void individualCannotChooseBusinessHolder() throws Exception {
		sellerFacade.reject(adminId, sellerId, "다시 신청");
		sellerId = sellerFacade.apply(memberId, new SellerApplyRequestDto(
			SellerType.INDIVIDUAL, null, null, "김러너", "004", OLD_NUMBER,
			PayoutAccountHolderType.PERSONAL, true)).getId();
		approve();
		verifier.reset();

		modify(memberId, "088", NEW_NUMBER, "BUSINESS").andExpect(status().isBadRequest());

		assertThat(verifier.inquiries()).isEmpty();
	}

	@Test
	@DisplayName("은행 코드·계좌번호·명의 형식이 틀리면 400이고 은행을 부르지 않는다")
	void invalidInput() throws Exception {
		approve();

		modify(memberId, null, NEW_NUMBER, "PERSONAL").andExpect(status().isBadRequest());
		modify(memberId, "88", NEW_NUMBER, "PERSONAL").andExpect(status().isBadRequest());
		modify(memberId, "088", "333-2000-100123", "PERSONAL").andExpect(status().isBadRequest());
		modify(memberId, "088", NEW_NUMBER, null).andExpect(status().isBadRequest());

		assertThat(verifier.inquiries()).isEmpty();
	}

	@Test
	@DisplayName("오픈뱅킹은 DB 트랜잭션 밖에서 부른다")
	void verifiesOutsideTransaction() throws Exception {
		approve();

		modify(memberId, "088", NEW_NUMBER, "PERSONAL").andExpect(status().isOk());

		assertThat(verifier.transactionActive()).containsExactly(false);
	}

	@Test
	@DisplayName("같은 판매자가 동시에 두 번 수정해도 둘 다 끝나고 계좌는 둘 중 하나로 온전하게 남는다")
	void concurrentModify() throws Exception {
		approve();
		CountDownLatch bothVerifying = new CountDownLatch(2);
		verifier.answer(inquiry -> {
			bothVerifying.countDown();
			try {
				bothVerifying.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
			return inquiry.applicantName();
		});

		ExecutorService executor = Executors.newFixedThreadPool(2);
		List<Future<?>> futures = new ArrayList<>();
		for (String number : List.of(NEW_NUMBER, "3332000100456")) {
			futures.add(executor.submit(() -> sellerFacade.modifyMyPayoutAccount(memberId,
				new SellerPayoutAccountModifyRequestDto("088", number, PayoutAccountHolderType.PERSONAL))));
		}
		for (Future<?> future : futures) {
			future.get(10, TimeUnit.SECONDS);
		}
		executor.shutdown();

		assertThat(sellerRepository.count()).isEqualTo(1);
		Seller saved = sellerRepository.findById(sellerId).orElseThrow();
		assertThat(saved.getPayoutBankCode()).isEqualTo("088");
		assertThat(saved.getPayoutAccountNumber()).isIn(NEW_NUMBER, "3332000100456");
	}

	private void assertOldAccountKept() {
		Seller seller = sellerRepository.findById(sellerId).orElseThrow();
		assertThat(seller.getPayoutBankCode()).isEqualTo("004");
		assertThat(seller.getPayoutAccountNumber()).isEqualTo(OLD_NUMBER);
		assertThat(seller.getPayoutAccountHolderName()).isEqualTo("김러너");
	}

	private ResultActions modify(long memberId, String bankCode, String accountNumber, String holderType)
		throws Exception {
		return mockMvc.perform(put(MY_ACCOUNT)
			.header(MemberIdHeader.NAME, memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
					"bankCode": %s,
					"accountNumber": %s,
					"accountHolderType": %s
				}
				""".formatted(json(bankCode), json(accountNumber), json(holderType))));
	}

	private String json(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}
}
