package com.bukang.cash.app;

import org.springframework.stereotype.Service;

import com.bukang.cash.app.CashSupport.LockedWallets;
import com.bukang.cash.domain.exception.PaymentMemberMismatchException;
import com.bukang.cash.domain.payment.Payment;
import com.bukang.cash.domain.payment.PaymentStatus;
import com.bukang.cash.domain.wallet.LedgerRefType;
import com.bukang.cash.domain.wallet.LedgerType;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.PaymentRepository;
import com.bukang.cash.out.WalletLedgerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashCancelOrderPaymentUseCase {
	private final CashSupport cashSupport;
	private final PaymentRepository paymentRepository;
	private final WalletLedgerRepository walletLedgerRepository;

	// 몇 번을, 언제 불러도 안전하다: 결제 전이면 선점해서 늦게 오는 pay를 거절하고,
	// 결제됐으면 돌려주고, 이미 취소·환불됐으면 그냥 성공(아무것도 안 바꿈)한다
	public void cancelPayment(long orderId, long customerId) {
		//1.고객 지갑이 없으면 지갑 없이는 결제도 성공할 수 없었으므로 취소할 것도 없이 성공으로 끝낸다
		if (!cashSupport.hasWallet(customerId)) {
			log.warn("지갑 없는 회원의 결제 취소 요청, 아무것도 안 바꾸고 끝냄. orderId={}, customerId={}",
				orderId, customerId);
			return;
		}

		//2.지갑 잠금 (고객 지갑, 보관 지갑 / ID 작은 쪽부터)
		LockedWallets lockedWallets = cashSupport.lockCustomerAndHoldingWallets(customerId);
		Wallet customerWallet = lockedWallets.customerWallet();
		Wallet holdingWallet = lockedWallets.holdingWallet();

		//3.결제 행 조회, 없으면 새로 만들고 바로 선점(CANCELED)한다
		Payment payment = cashSupport.findByOrderId(orderId)
			.orElseGet(() -> paymentRepository.save(new Payment(orderId, customerWallet, 0)));

		//4.결제 행의 실제 주인(지갑)과 요청받은 회원이 다르면 거절한다 (다른 회원 지갑으로 환원되는 것을 막음)
		if (payment.getWallet().getId() != customerWallet.getId()) {
			throw new PaymentMemberMismatchException("결제한 회원과 요청한 회원이 다릅니다.");
		}

		//5.이미 취소·환불됐으면 그대로 끝 (멱등)
		if (payment.getStatus() == PaymentStatus.CANCELED || payment.getStatus() == PaymentStatus.REFUNDED) {
			return;
		}

		//6.결제됐으면(COMPLETED) 반대 방향 장부 2줄을 남기고 지갑을 되돌린다
		//  결제 전(PENDING/FAILED거나 방금 선점용으로 만든 행)이면 지갑은 안 건드리고 상태만 CANCELED로 선점한다
		if (payment.getStatus() == PaymentStatus.COMPLETED) {
			long amount = payment.getAmount();
			walletLedgerRepository.save(
				customerWallet.credit(amount, LedgerType.환불__주문취소, LedgerRefType.Order, orderId));
			walletLedgerRepository.save(
				holdingWallet.debit(amount, LedgerType.임시보관반환__주문취소, LedgerRefType.Order, orderId));
		}

		payment.markCanceled();
	}
}
