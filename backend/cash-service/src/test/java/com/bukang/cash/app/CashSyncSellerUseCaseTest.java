package com.bukang.cash.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.seller.CashSeller;
import com.bukang.cash.out.CashMemberRepository;
import com.bukang.cash.out.CashSellerRepository;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.common.shared.seller.event.SellerChangedEvent;

// PRO-256: 정산 지급 때 받는 sellerId(판매자 신청서 ID)를 회원 ID로 바꾸려면 이 복제본이 있어야 한다
@SpringBootTest
@ActiveProfiles("test")
class CashSyncSellerUseCaseTest {
	private static final long SELLER_APPLICATION_ID = 50;
	private static final long MEMBER_ID = 10;

	@Autowired
	private CashFacade cashFacade;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private CashSellerRepository cashSellerRepository;

	@AfterEach
	void tearDown() {
		cashSellerRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("회원 복제본이 있으면 판매자 변경 이벤트로 CashSeller(신청서 ID → 회원)가 만들어진다")
	void syncSeller_withExistingMember_createsMapping() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 20, 10, 0);
		cashMemberRepository.save(new CashMember(MEMBER_ID, now, now, "판매자"));

		cashFacade.syncSeller(sellerChangedEvent(SellerStatus.PENDING, now));

		CashSeller seller = cashSellerRepository.findById(SELLER_APPLICATION_ID).orElseThrow();
		assertThat(seller.getMember().getId()).isEqualTo(MEMBER_ID);
	}

	@Test
	@DisplayName("승인·반려로 같은 신청서 ID가 다시 와도 한 행으로 덮어쓴다")
	void syncSeller_sameApplicationIdTwice_overwritesSameRow() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 20, 10, 0);
		cashMemberRepository.save(new CashMember(MEMBER_ID, now, now, "판매자"));
		cashFacade.syncSeller(sellerChangedEvent(SellerStatus.PENDING, now));

		cashFacade.syncSeller(sellerChangedEvent(SellerStatus.APPROVED, now));

		assertThat(cashSellerRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("회원 복제본이 없으면 CashSeller를 만들지 않고 건너뛴다")
	void syncSeller_withoutMember_skips() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 20, 10, 0);

		cashFacade.syncSeller(sellerChangedEvent(SellerStatus.PENDING, now));

		assertThat(cashSellerRepository.findById(SELLER_APPLICATION_ID)).isEmpty();
	}

	private SellerChangedEvent sellerChangedEvent(SellerStatus status, LocalDateTime now) {
		return new SellerChangedEvent(
			SELLER_APPLICATION_ID, MEMBER_ID, SellerType.INDIVIDUAL, null, null, "김판매", status, now, now);
	}
}
