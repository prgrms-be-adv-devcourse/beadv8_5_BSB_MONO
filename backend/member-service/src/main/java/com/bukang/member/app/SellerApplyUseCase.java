package com.bukang.member.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.Seller;
import com.bukang.member.domain.exception.MemberNotFoundException;
import com.bukang.member.domain.exception.SellerAlreadyAppliedException;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.SellerRepository;

import lombok.RequiredArgsConstructor;

// 판매자 등록 신청 (BE-020). 신청마다 새 행을 만들고, 대기·승인 상태 신청이 있으면 막는다
@Service
@RequiredArgsConstructor
public class SellerApplyUseCase {
	// 이 상태의 신청이 있으면 다시 신청할 수 없다. 반려(REJECTED)만 남아 있으면 새로 신청한다
	private static final List<SellerStatus> ACTIVE_STATUSES = List.of(SellerStatus.PENDING, SellerStatus.APPROVED);

	private final MemberRepository memberRepository;
	private final SellerRepository sellerRepository;

	public Seller apply(
		long memberId,
		SellerType sellerType,
		String businessName,
		String businessNumber,
		String representativeName,
		LocalDateTime now
	) {
		// 탈퇴한 회원은 남은 Access Token으로 불러도 없는 회원으로 본다 (PRO-217, BE-022 결정 6)
		// 회원 줄을 잠가 탈퇴와 신청의 순서를 정한다. 탈퇴가 먼저 커밋되면 여기서 탈퇴한 회원을 보고 404가 된다
		Member member = memberRepository.findByIdForUpdate(memberId)
			.filter(found -> !found.isWithdrawn())
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."));
		if (sellerRepository.existsByMemberIdAndStatusIn(memberId, ACTIVE_STATUSES)) {
			throw new SellerAlreadyAppliedException("이미 심사 중이거나 승인된 판매자 신청이 있습니다.");
		}

		// 저장해야 ID가 정해지므로(IDENTITY) 바로 반영한 뒤 이벤트를 발행한다 (가입 이벤트와 같은 이유)
		Seller savedSeller = sellerRepository.saveAndFlush(
			new Seller(member, sellerType, businessName, businessNumber, representativeName, now)
		);
		savedSeller.publishChangedEvent();
		return savedSeller;
	}
}
