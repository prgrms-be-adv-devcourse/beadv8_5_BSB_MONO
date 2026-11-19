package com.bukang.member.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.domain.Seller;
import com.bukang.member.domain.VerifiedPayoutAccount;
import com.bukang.member.domain.exception.SellerNotFoundException;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.SellerRepository;

import lombok.RequiredArgsConstructor;

/**
 * 계좌실명조회를 통과한 정산 계좌를 승인된 판매자 행에 저장한다 (CRU-80)
 * 회원 줄과 판매자 줄을 차례로 잠가, 같은 판매자의 동시 수정과 탈퇴·심사 상태 변화와 순서를 정한다
 * 확인(오픈뱅킹)은 PayoutAccountVerifyUseCase가 트랜잭션 밖에서 끝냈다. 여기서 실패하면 기존 계좌가 그대로 남는다
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SellerPayoutAccountModifyUseCase {
	private final MemberRepository memberRepository;
	private final SellerRepository sellerRepository;

	public Seller modify(long memberId, VerifiedPayoutAccount payoutAccount) {
		memberRepository.getActiveForUpdate(memberId);
		Seller seller = sellerRepository.findLatestByMemberIdForUpdate(memberId)
			.orElseThrow(() -> new SellerNotFoundException("판매자 신청 내역이 없습니다."));
		seller.changePayoutAccount(payoutAccount);
		return seller;
	}
}
