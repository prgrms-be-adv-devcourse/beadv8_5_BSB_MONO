package com.bukang.member.app;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.bukang.common.shared.seller.event.SellerAccountRegisteredEvent;
import com.bukang.member.domain.Seller;
import com.bukang.member.domain.VerifiedPayoutAccount;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.SellerRepository;

class SellerPayoutAccountModifyUseCaseTest {
	@Test
	@DisplayName("정산 계좌를 저장하면 그 판매자 ID로 「정산 계좌 등록됨」 이벤트를 아웃박스에 남긴다")
	void modifyAppendsSellerAccountRegistered() {
		SellerRepository sellerRepository = mock(SellerRepository.class);
		MemberOutboxAppendUseCase outbox = mock(MemberOutboxAppendUseCase.class);
		Seller seller = mock(Seller.class);
		VerifiedPayoutAccount account = mock(VerifiedPayoutAccount.class);
		given(sellerRepository.findLatestByMemberIdForUpdate(1L)).willReturn(Optional.of(seller));
		given(seller.getId()).willReturn(1000L);

		new SellerPayoutAccountModifyUseCase(mock(MemberRepository.class), sellerRepository, outbox)
			.modify(1L, account);

		// 승인 안 된 판매자면 changePayoutAccount가 던져 이벤트가 나가지 않도록, 저장 뒤에 남긴다
		InOrder order = inOrder(seller, outbox);
		order.verify(seller).changePayoutAccount(account);
		order.verify(outbox).appendSellerAccountRegistered(new SellerAccountRegisteredEvent(1000));
	}
}
