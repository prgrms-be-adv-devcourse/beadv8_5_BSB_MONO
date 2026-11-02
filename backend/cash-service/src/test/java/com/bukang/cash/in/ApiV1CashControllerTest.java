package com.bukang.cash.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.WalletLedgerRepository;
import com.bukang.cash.out.WalletRepository;
import com.bukang.cash.out.WalletWithdrawalRepository;
import com.bukang.common.global.web.IdempotencyKeyHeader;
import com.bukang.common.shared.member.web.MemberIdHeader;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiV1CashControllerTest {
	// 충전 확정 요청 오류 테스트는 컨트롤러 검증에서 끝나므로 PG(Toss)를 부르지 않는다
	private static final String CHARGE_CONFIRM_BODY = """
		{ "paymentKey": "5EnNZRJGvaBd3GirPNB6s", "orderId": "charge-1697000000000", "amount": 10000 }
		""";
	private static final String WITHDRAWAL_BODY = """
		{ "bankCode": "004", "accountNumber": "110123456789", "accountHolder": "홍길동" }
		""";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletLedgerRepository walletLedgerRepository;
	@Autowired
	private WalletWithdrawalRepository walletWithdrawalRepository;

	@Test
	@DisplayName("잔액 조회: 지갑이 있으면 200과 RsData 형식으로 요청자 본인의 잔액을 돌려준다")
	void getBalance() throws Exception {
		LocalDateTime now = LocalDateTime.of(2026, 10, 12, 10, 0);
		CashMember member = cashMemberRepository.save(new CashMember(3, now, now, "러너3"));
		walletRepository.save(new Wallet(member));

		mockMvc.perform(get("/api/v1/cash/wallets/balance")
				.header(MemberIdHeader.NAME, "3"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("잔액 조회 성공"))
			.andExpect(jsonPath("$.data.holderId").value(3))
			.andExpect(jsonPath("$.data.balance").value(0));
	}

	@Test
	@DisplayName("잔액 조회: 지갑이 없으면 404와 에러 메시지를 돌려준다")
	void getBalanceWithoutWallet() throws Exception {
		mockMvc.perform(get("/api/v1/cash/wallets/balance")
				.header(MemberIdHeader.NAME, "999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.message").value("존재하지 않는 지갑입니다."))
			.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	@DisplayName("잔액 조회: 회원 ID 헤더가 없으면 401과 RsData 형식으로 로그인 필요를 알린다")
	void getBalanceWithoutMemberId() throws Exception {
		mockMvc.perform(get("/api/v1/cash/wallets/balance"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"))
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
			.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	@DisplayName("잔액 조회: 회원 ID가 0 이하면 400 INVALID_REQUEST로 응답한다")
	void getBalanceWithNonPositiveMemberId() throws Exception {
		mockMvc.perform(get("/api/v1/cash/wallets/balance")
				.header(MemberIdHeader.NAME, "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value(MemberIdHeader.INVALID_MESSAGE));
	}

	@Test
	@DisplayName("잔액 조회: 회원 ID가 숫자가 아니면 400 INVALID_REQUEST로 응답한다")
	void getBalanceWithNonNumericMemberId() throws Exception {
		mockMvc.perform(get("/api/v1/cash/wallets/balance")
				.header(MemberIdHeader.NAME, "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value(MemberIdHeader.INVALID_MESSAGE));
	}

	@Test
	@DisplayName("내역 조회: 전체 조회 시 카테고리가 섞여도 최신순으로 PageDto 형식으로 돌려주고, "
		+ "다른 지갑(홀딩·다른 회원) 줄은 안 섞인다")
	void getLedgers() throws Exception {
		Wallet wallet = createWalletWithBalance(3, 10_000);
		walletLedgerRepository.save(wallet.debit(4_000, LedgerType.사용__주문결제, LedgerRefType.Order, 100));
		walletLedgerRepository.save(wallet.credit(4_000, LedgerType.환불__주문취소, LedgerRefType.Order, 100));
		// 같은 주문으로 홀딩 지갑에도 짝이 되는 줄이 남는다 (장부 기록 규칙) — 요청자(3번) 조회엔 안 섞여야 한다
		Wallet holdingWallet = createWalletWithBalance(2, 0);
		walletLedgerRepository.save(holdingWallet.credit(4_000, LedgerType.임시보관__주문결제, LedgerRefType.Order, 100));
		// 다른 회원(4번)의 내역도 안 섞여야 한다
		Wallet otherWallet = createWalletWithBalance(4, 10_000);
		walletLedgerRepository.save(otherWallet.debit(4_000, LedgerType.사용__주문결제, LedgerRefType.Order, 200));

		mockMvc.perform(get("/api/v1/cash/wallets/ledgers")
				.header(MemberIdHeader.NAME, "3"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content.length()").value(3))
			.andExpect(jsonPath("$.data.content[0].category").value("REFUND"))
			.andExpect(jsonPath("$.data.content[0].ledgerType").value("환불__주문취소"))
			.andExpect(jsonPath("$.data.content[0].amount").value(4_000))
			.andExpect(jsonPath("$.data.content[0].balanceAfter").value(10_000))
			.andExpect(jsonPath("$.data.content[0].refId").value(100))
			.andExpect(jsonPath("$.data.content[1].category").value("PAYMENT"))
			.andExpect(jsonPath("$.data.content[1].amount").value(-4_000))
			.andExpect(jsonPath("$.data.content[2].category").value("CHARGE"))
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.totalElements").value(3));
	}

	@Test
	@DisplayName("내역 조회: category로 넘기면 그 탭에 해당하는 내역만 돌려준다")
	void getLedgersFilteredByCategory() throws Exception {
		Wallet wallet = createWalletWithBalance(3, 10_000);
		walletLedgerRepository.save(wallet.debit(4_000, LedgerType.사용__주문결제, LedgerRefType.Order, 100));

		mockMvc.perform(get("/api/v1/cash/wallets/ledgers")
				.header(MemberIdHeader.NAME, "3")
				.param("category", "PAYMENT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content.length()").value(1))
			.andExpect(jsonPath("$.data.content[0].category").value("PAYMENT"));
	}

	@Test
	@DisplayName("내역 조회: 페이지 크기가 100을 넘으면 400 INVALID_REQUEST로 응답한다")
	void getLedgersWithTooLargePageSize() throws Exception {
		mockMvc.perform(get("/api/v1/cash/wallets/ledgers")
				.header(MemberIdHeader.NAME, "3")
				.param("size", "101"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("한 번에 100개까지 조회할 수 있습니다."));
	}

	@Test
	@DisplayName("내역 조회: 지갑이 없으면 404와 에러 메시지를 돌려준다")
	void getLedgersWithoutWallet() throws Exception {
		mockMvc.perform(get("/api/v1/cash/wallets/ledgers")
				.header(MemberIdHeader.NAME, "999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 지갑입니다."));
	}

	@Test
	@DisplayName("내역 조회: 회원 ID 헤더가 없으면 401과 RsData 형식으로 로그인 필요를 알린다")
	void getLedgersWithoutMemberId() throws Exception {
		mockMvc.perform(get("/api/v1/cash/wallets/ledgers"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
	}

	@Test
	@DisplayName("충전 확정: 회원 ID 헤더가 없으면 401과 RsData 형식으로 로그인 필요를 알린다")
	void confirmChargeWithoutMemberId() throws Exception {
		mockMvc.perform(post("/api/v1/cash/charges/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CHARGE_CONFIRM_BODY))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
			.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	@DisplayName("충전 확정: 회원 ID가 0 이하면 400 INVALID_REQUEST로 응답한다")
	void confirmChargeWithNonPositiveMemberId() throws Exception {
		mockMvc.perform(post("/api/v1/cash/charges/confirm")
				.header(MemberIdHeader.NAME, "0")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CHARGE_CONFIRM_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value(MemberIdHeader.INVALID_MESSAGE));
	}

	@Test
	@DisplayName("충전 확정: 회원 ID가 숫자가 아니면 400 INVALID_REQUEST로 응답한다")
	void confirmChargeWithNonNumericMemberId() throws Exception {
		mockMvc.perform(post("/api/v1/cash/charges/confirm")
				.header(MemberIdHeader.NAME, "abc")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CHARGE_CONFIRM_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value(MemberIdHeader.INVALID_MESSAGE));
	}

	@Test
	@DisplayName("충전 확정: 요청 본문 검증에 실패하면 400 INVALID_REQUEST와 첫 오류 메시지로 응답한다")
	void confirmChargeWithInvalidBody() throws Exception {
		mockMvc.perform(post("/api/v1/cash/charges/confirm")
				.header(MemberIdHeader.NAME, "3")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "paymentKey": "", "orderId": "charge-1697000000000", "amount": 10000 }
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("paymentKey를 작성해주세요."));
	}

	@Test
	@DisplayName("환급 신청: Idempotency-Key 헤더 값을 멱등키로 저장하고, 같은 키로 다시 보내면 처음 결과를 돌려준다")
	void requestWithdrawalWithIdempotencyKeyHeader() throws Exception {
		createWalletWithBalance(3, 50_000);

		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(post("/api/v1/cash/withdrawals")
					.header(MemberIdHeader.NAME, "3")
					.header(IdempotencyKeyHeader.NAME, "withdrawal-key-1")
					.contentType(MediaType.APPLICATION_JSON)
					.content(WITHDRAWAL_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("환급 신청이 완료되었습니다."))
				.andExpect(jsonPath("$.data.withdrawnAmount").value(50_000))
				.andExpect(jsonPath("$.data.balance").value(0));
		}

		assertThat(walletWithdrawalRepository.findByIdempotencyKey("withdrawal-key-1")).isPresent();
		assertThat(walletWithdrawalRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("환급 신청: Idempotency-Key 헤더가 없으면 400 INVALID_REQUEST로 응답한다")
	void requestWithdrawalWithoutIdempotencyKey() throws Exception {
		createWalletWithBalance(3, 50_000);

		mockMvc.perform(post("/api/v1/cash/withdrawals")
				.header(MemberIdHeader.NAME, "3")
				.contentType(MediaType.APPLICATION_JSON)
				.content(WITHDRAWAL_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("필수 요청 헤더(Idempotency-Key)가 없습니다."));
		assertThat(walletWithdrawalRepository.count()).isZero();
	}

	@Test
	@DisplayName("환급 신청: Idempotency-Key가 64자를 넘으면 400 INVALID_REQUEST로 응답한다")
	void requestWithdrawalWithTooLongIdempotencyKey() throws Exception {
		createWalletWithBalance(3, 50_000);

		mockMvc.perform(post("/api/v1/cash/withdrawals")
				.header(MemberIdHeader.NAME, "3")
				.header(IdempotencyKeyHeader.NAME, "k".repeat(IdempotencyKeyHeader.MAX_LENGTH + 1))
				.contentType(MediaType.APPLICATION_JSON)
				.content(WITHDRAWAL_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value(IdempotencyKeyHeader.INVALID_MESSAGE));
		assertThat(walletWithdrawalRepository.count()).isZero();
	}

	private Wallet createWalletWithBalance(long memberId, long balance) {
		LocalDateTime now = LocalDateTime.of(2026, 10, 12, 10, 0);
		CashMember member = cashMemberRepository.save(new CashMember(memberId, now, now, "러너" + memberId));
		Wallet wallet = walletRepository.save(new Wallet(member));
		walletLedgerRepository.save(wallet.credit(balance, LedgerType.충전__PG결제_토스페이먼츠, LedgerRefType.WalletCharge, 1));
		return wallet;
	}
}
