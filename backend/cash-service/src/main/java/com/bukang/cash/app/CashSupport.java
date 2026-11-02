package com.bukang.cash.app;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bukang.cash.config.CashWalletProperties;
import com.bukang.cash.domain.exception.CashSellerNotFoundException;
import com.bukang.cash.domain.exception.WalletNotFoundException;
import com.bukang.cash.domain.payment.Payment;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.CashSellerRepository;
import com.bukang.cash.out.PaymentRepository;
import com.bukang.cash.out.WalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CashSupport {
	private final WalletRepository walletRepository;
	private final PaymentRepository paymentRepository;
	private final CashWalletProperties cashWalletProperties;
	private final CashSellerRepository cashSellerRepository;

	// 지갑 ID = 회원 ID. 지갑은 가입 이벤트로 만들어지므로, 없으면 조회 시점에 만들지 않고 404로 알린다
	public Wallet findWalletByHolderId(long holderId) {
		return walletRepository.findById(holderId)
			.orElseThrow(() -> new WalletNotFoundException("존재하지 않는 지갑입니다."));
	}

	// 홀딩 지갑도 회원 가입 이벤트로 만들어진다 (member-service가 1·2번을 시스템·홀딩 회원으로 둔다)
	public Wallet findHoldingWallet() {
		return findWalletByHolderId(cashWalletProperties.holdingMemberId());
	}

	public Wallet findSystemWallet() {
		return findWalletByHolderId(cashWalletProperties.systemMemberId());
	}

	// 회원 탈퇴 확인용 잔액 (BE-022). 지갑이 없으면 남은 예치금도 없으므로 404 대신 0원으로 본다
	public long findBalanceOrZero(long holderId) {
		return walletRepository.findById(holderId)
			.map(Wallet::getBalance)
			.orElse(0L);
	}

	// 잠그지 않고 있는지만 본다 (환원처럼 "지갑이 없으면 애초에 결제된 적도 없다"를 판단할 때 쓴다)
	public boolean hasWallet(long holderId) {
		return walletRepository.existsById(holderId);
	}

	//주문 존재 확인
	public Optional<Payment> findByOrderId(long orderId) {
		return paymentRepository.findByOrderId(orderId);
	}

	// 지갑 하나만 비관적 락으로 잠근다 (충전 확정처럼 다른 지갑과 같이 잠글 필요가 없을 때 쓴다, PRO-215)
	public Wallet lockWallet(long holderId) {
		return walletRepository.findByIdForUpdate(holderId)
			.orElseThrow(() -> new WalletNotFoundException("존재하지 않는 지갑입니다."));
	}

	// 여러 지갑을 회원 ID 오름차순으로 비관적 락으로 잠근다 (교착 상태 방지, PRO-78을 임의 개수로 확장, PRO-256)
	// 지갑 ID가 회원 ID와 항상 같을 때만 올바르다. 지갑 ID가 자동 증가(BaseIdAndTime)라면
	// 다른 회원의 지갑을 잠글 수 있으므로, 회원 ID로 잠그는 findByMemberIdForUpdate를 써야 한다.
	public Map<Long, Wallet> lockWallets(long... holderIds) {
		Map<Long, Wallet> locked = new LinkedHashMap<>();
		Arrays.stream(holderIds)
			.distinct()
			.sorted()
			.forEach(id -> locked.put(id, walletRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new WalletNotFoundException("존재하지 않는 지갑입니다."))));
		return locked;
	}

	// 고객 지갑·보관 지갑을 함께 잠근다 (PRO-78)
	public LockedWallets lockCustomerAndHoldingWallets(long customerId) {
		long holdingMemberId = cashWalletProperties.holdingMemberId();
		Map<Long, Wallet> wallets = lockWallets(customerId, holdingMemberId);
		return new LockedWallets(wallets.get(customerId), wallets.get(holdingMemberId));
	}

	// 정산 지급 때 받는 sellerId는 판매자 신청서 ID라 회원 ID(= 지갑 ID)와 다르다.
	// CashSeller 복제본으로 실제 지갑 주인(회원 ID)을 찾는다 (PRO-256 리뷰 반영)
	public long resolveSellerMemberId(long sellerId) {
		return cashSellerRepository.findById(sellerId)
			.map(seller -> seller.getMember().getId())
			.orElseThrow(() -> new CashSellerNotFoundException("존재하지 않는 판매자입니다. sellerId=%d".formatted(sellerId)));
	}

	public record LockedWallets(Wallet customerWallet, Wallet holdingWallet) {
	}

}
