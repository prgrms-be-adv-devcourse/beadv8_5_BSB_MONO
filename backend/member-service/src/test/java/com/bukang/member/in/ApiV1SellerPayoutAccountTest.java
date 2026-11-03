package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
import com.bukang.member.domain.exception.SellerAlreadyAppliedException;
import com.bukang.member.dto.SellerApplyRequestDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.SellerRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.payoutaccount.InMemoryPayoutAccountVerifyLimiter;
import com.bukang.member.out.payoutaccount.PayoutAccountInquiry;
import com.bukang.member.out.payoutaccount.StubPayoutAccountVerifier;
import com.bukang.member.support.MemberFixture;

// 판매자 신청 때 정산 계좌 확인 (BE-031). 오픈뱅킹 대신 StubPayoutAccountVerifier가 답한다
// 가입한 회원의 본인인증 이름은 김러너, 생년월일은 1995-04-12다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@Import({InMemoryIdentityVerificationStore.Config.class, InMemoryPayoutAccountVerifyLimiter.Config.class,
	StubPayoutAccountVerifier.Config.class})
class ApiV1SellerPayoutAccountTest {
	private static final String SELLERS = "/api/v1/member/sellers";
	private static final String ACCOUNT_NUMBER = "1101230000678";

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

	@BeforeEach
	void setUp() {
		verifier.reset();
		limiter.clear();
		// 픽스처 기본값: 이름 김러너, 생년월일 1995-04-12
		memberId = memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
	}

	@AfterEach
	void tearDown() {
		sellerRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("본인 명의는 본인인증 생년월일로 조회하고, 예금주 이름이 공백만 달라도 통과해 계좌를 암호화해 저장한다")
	void personalAccountSaved(CapturedOutput output) throws Exception {
		verifier.answer(inquiry -> "김 러너");

		apply("INDIVIDUAL", null, null, "004", ACCOUNT_NUMBER, "PERSONAL")
			.andExpect(status().isCreated());

		PayoutAccountInquiry inquiry = verifier.inquiries().getFirst();
		assertThat(inquiry.bankCode()).isEqualTo("004");
		assertThat(inquiry.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
		assertThat(inquiry.holderType()).isEqualTo(PayoutAccountHolderType.PERSONAL);
		assertThat(inquiry.holderInfo()).isEqualTo("950412");

		Seller saved = sellerRepository.findFirstByMemberIdOrderByIdDesc(memberId).orElseThrow();
		assertThat(saved.getPayoutBankCode()).isEqualTo("004");
		assertThat(saved.getPayoutAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
		assertThat(saved.getPayoutAccountHolderName()).isEqualTo("김 러너");
		assertThat(saved.getPayoutAccountHolderType()).isEqualTo(PayoutAccountHolderType.PERSONAL);
		assertThat(saved.getPayoutAccountVerifiedAt()).isNotNull();

		String stored = jdbcTemplate.queryForObject(
			"select payout_account_number from member_seller where seller_id = ?", String.class, saved.getId());
		assertThat(stored).isNotBlank().doesNotContain(ACCOUNT_NUMBER);
		assertThat(output).doesNotContain(ACCOUNT_NUMBER);
	}

	@Test
	@DisplayName("사업자 명의는 신청서의 사업자등록번호 숫자로 조회하고, 은행이 맞다고 답하면 이름 비교 없이 통과한다")
	void businessAccountSaved() throws Exception {
		verifier.answer(inquiry -> "(주)크루런스포츠");

		apply("BUSINESS", "크루런 스포츠", "123-45-67890", "004", ACCOUNT_NUMBER, "BUSINESS")
			.andExpect(status().isCreated());

		PayoutAccountInquiry inquiry = verifier.inquiries().getFirst();
		assertThat(inquiry.holderType()).isEqualTo(PayoutAccountHolderType.BUSINESS);
		assertThat(inquiry.holderInfo()).isEqualTo("1234567890");
		Seller saved = sellerRepository.findFirstByMemberIdOrderByIdDesc(memberId).orElseThrow();
		assertThat(saved.getPayoutAccountHolderName()).isEqualTo("(주)크루런스포츠");
		assertThat(saved.getPayoutAccountHolderType()).isEqualTo(PayoutAccountHolderType.BUSINESS);
	}

	@Test
	@DisplayName("본인 명의인데 예금주 이름이 본인인증 이름과 다르면 400이고 신청이 생기지 않는다")
	void personalNameMismatch() throws Exception {
		verifier.answer(inquiry -> "박러너");

		apply("INDIVIDUAL", null, null, "004", ACCOUNT_NUMBER, "PERSONAL")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SELLER_ACCOUNT_NOT_VERIFIED"))
			.andExpect(jsonPath("$.message").value("예금주 정보가 맞지 않아요. 계좌번호와 계좌 명의를 확인해 주세요."));

		assertThat(sellerRepository.count()).isZero();
	}

	@Test
	@DisplayName("은행이 맞지 않다고 답하면 400이고 신청이 생기지 않는다")
	void bankRejects() throws Exception {
		verifier.answer(inquiry -> {
			throw new SellerAccountNotVerifiedException();
		});

		apply("BUSINESS", "크루런 스포츠", "123-45-67890", "004", ACCOUNT_NUMBER, "BUSINESS")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SELLER_ACCOUNT_NOT_VERIFIED"));

		assertThat(sellerRepository.count()).isZero();
	}

	@Test
	@DisplayName("오픈뱅킹이 응답하지 않으면 503이고 신청이 생기지 않는다")
	void openBankingUnavailable() throws Exception {
		verifier.answer(inquiry -> {
			throw new SellerAccountVerifyUnavailableException();
		});

		apply("INDIVIDUAL", null, null, "004", ACCOUNT_NUMBER, "PERSONAL")
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("SELLER_ACCOUNT_VERIFY_UNAVAILABLE"))
			.andExpect(jsonPath("$.message").value("잠시 뒤 다시 시도해 주세요."));

		assertThat(sellerRepository.count()).isZero();
	}

	@Test
	@DisplayName("하루 다섯 번 확인한 뒤 여섯 번째는 오픈뱅킹을 부르기 전에 429로 막는다")
	void dailyLimit() throws Exception {
		verifier.answer(inquiry -> "박러너");
		for (int i = 0; i < 5; i++) {
			apply("INDIVIDUAL", null, null, "004", ACCOUNT_NUMBER, "PERSONAL")
				.andExpect(status().isBadRequest());
		}

		apply("INDIVIDUAL", null, null, "004", ACCOUNT_NUMBER, "PERSONAL")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("SELLER_ACCOUNT_VERIFY_TOO_MANY_REQUESTS"));

		assertThat(verifier.inquiries()).hasSize(5);
	}

	@Test
	@DisplayName("오픈뱅킹이 응답하지 않은 확인은 하루 횟수에서 빼, 장애가 다섯 번 나도 다시 확인할 수 있다")
	void unavailableDoesNotUseQuota() throws Exception {
		verifier.answer(inquiry -> {
			throw new SellerAccountVerifyUnavailableException();
		});
		for (int i = 0; i < 5; i++) {
			apply("INDIVIDUAL", null, null, "004", ACCOUNT_NUMBER, "PERSONAL")
				.andExpect(status().isServiceUnavailable());
		}
		verifier.answer(PayoutAccountInquiry::applicantName);

		apply("INDIVIDUAL", null, null, "004", ACCOUNT_NUMBER, "PERSONAL")
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("오픈뱅킹은 DB 트랜잭션 밖에서 부른다")
	void verifiesOutsideTransaction() throws Exception {
		apply("INDIVIDUAL", null, null, "004", ACCOUNT_NUMBER, "PERSONAL")
			.andExpect(status().isCreated());

		assertThat(verifier.transactionActive()).containsExactly(false);
	}

	@Test
	@DisplayName("대기 중인 신청이 있으면 오픈뱅킹을 부르지 않고 409이며 조회 횟수도 쓰지 않는다")
	void alreadyAppliedSkipsInquiry() throws Exception {
		apply("INDIVIDUAL", null, null, "004", ACCOUNT_NUMBER, "PERSONAL")
			.andExpect(status().isCreated());
		verifier.reset();

		apply("INDIVIDUAL", null, null, "004", ACCOUNT_NUMBER, "PERSONAL")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SELLER_ALREADY_APPLIED"));

		assertThat(verifier.inquiries()).isEmpty();
	}

	@Test
	@DisplayName("같은 회원이 동시에 두 번 신청해 둘 다 계좌 확인을 지나도 신청은 하나만 생긴다")
	void concurrentApplyCreatesOne() throws Exception {
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
		SellerApplyRequestDto request = new SellerApplyRequestDto(SellerType.INDIVIDUAL, null, null, "김러너",
			"004", ACCOUNT_NUMBER, PayoutAccountHolderType.PERSONAL);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			futures.add(executor.submit(() -> sellerFacade.apply(memberId, request)));
		}
		int conflicts = 0;
		for (Future<?> future : futures) {
			try {
				future.get(10, TimeUnit.SECONDS);
			} catch (ExecutionException exception) {
				assertThat(exception.getCause()).isInstanceOf(SellerAlreadyAppliedException.class);
				conflicts++;
			}
		}
		executor.shutdown();

		assertThat(conflicts).isEqualTo(1);
		assertThat(sellerRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("비사업자는 사업자 명의 계좌를 고를 수 없어 400이다")
	void individualCannotChooseBusinessHolder() throws Exception {
		apply("INDIVIDUAL", null, null, "004", ACCOUNT_NUMBER, "BUSINESS")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("사업자 명의 계좌는 사업자만 고를 수 있습니다."));

		assertThat(verifier.inquiries()).isEmpty();
	}

	@Test
	@DisplayName("은행·계좌번호·계좌 명의가 비었거나 형식이 틀리면 400이다")
	void invalidAccountInput() throws Exception {
		apply("INDIVIDUAL", null, null, null, ACCOUNT_NUMBER, "PERSONAL")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("은행을 선택해주세요."));
		apply("INDIVIDUAL", null, null, "04", ACCOUNT_NUMBER, "PERSONAL")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("은행 코드는 숫자 3자리입니다."));
		apply("INDIVIDUAL", null, null, "004", "110-123-000067", "PERSONAL")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("계좌번호는 숫자만 6~16자리로 작성해주세요."));
		apply("INDIVIDUAL", null, null, "004", ACCOUNT_NUMBER, null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("계좌 명의를 선택해주세요."));

		assertThat(verifier.inquiries()).isEmpty();
	}

	private ResultActions apply(String sellerType, String businessName, String businessNumber, String bankCode,
		String accountNumber, String holderType) throws Exception {
		return mockMvc.perform(post(SELLERS)
			.header(MemberIdHeader.NAME, memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
					"sellerType": %s,
					"businessName": %s,
					"businessNumber": %s,
					"representativeName": "김러너",
					"bankCode": %s,
					"accountNumber": %s,
					"accountHolderType": %s
				}
				""".formatted(json(sellerType), json(businessName), json(businessNumber), json(bankCode),
				json(accountNumber), json(holderType))));
	}

	private String json(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}
}
