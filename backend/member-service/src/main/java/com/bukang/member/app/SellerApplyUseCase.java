package com.bukang.member.app;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.Seller;
import com.bukang.member.domain.Terms;
import com.bukang.member.domain.TermsAgreement;
import com.bukang.member.domain.TermsType;
import com.bukang.member.domain.VerifiedPayoutAccount;
import com.bukang.member.domain.exception.RequiredTermsNotAgreedException;
import com.bukang.member.domain.exception.SellerAlreadyAppliedException;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.SellerRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.TermsRepository;

import lombok.RequiredArgsConstructor;

// 판매자 등록 신청 (BE-020). 신청마다 새 행을 만들고, 대기·승인 상태 신청이 있으면 막는다
// 정산 계좌는 트랜잭션 밖에서 미리 확인해 받는다 (BUK-50, PayoutAccountVerifyUseCase)
@Service
@Transactional
@RequiredArgsConstructor
public class SellerApplyUseCase {
	// 이 상태의 신청이 있으면 다시 신청할 수 없다. 반려(REJECTED)만 남아 있으면 새로 신청한다
	static final List<SellerStatus> ACTIVE_STATUSES = List.of(SellerStatus.PENDING, SellerStatus.APPROVED);

	private final Clock clock;
	private final MemberRepository memberRepository;
	private final SellerRepository sellerRepository;
	private final TermsRepository termsRepository;
	private final TermsAgreementRepository termsAgreementRepository;

	public Seller apply(
		long memberId,
		SellerType sellerType,
		String businessName,
		String businessNumber,
		String representativeName,
		VerifiedPayoutAccount payoutAccount,
		boolean sellerTermsAgreed,
		LocalDateTime now
	) {
		// 요청 검사(@AssertTrue)를 거치지 않고 서비스를 바로 불러도 동의 없이는 신청할 수 없다
		if (!sellerTermsAgreed) {
			throw new RequiredTermsNotAgreedException("판매자 약관에 동의해야 신청할 수 있습니다.");
		}

		// 탈퇴한 회원은 남은 Access Token으로 불러도 없는 회원으로 본다 (PRO-217, BE-022 결정 6)
		// 회원 줄을 잠가 탈퇴와 신청의 순서를 정한다. 탈퇴가 먼저 커밋되면 여기서 탈퇴한 회원을 보고 404가 된다
		// 같은 회원이 동시에 두 번 신청해 둘 다 계좌 확인을 지나도, 여기서 줄을 차례로 잡아 두 번째는 409가 된다
		Member member = memberRepository.getActiveForUpdate(memberId);
		if (sellerRepository.existsByMemberIdAndStatusIn(memberId, ACTIVE_STATUSES)) {
			throw new SellerAlreadyAppliedException("이미 심사 중이거나 승인된 판매자 신청이 있습니다.");
		}

		// 저장해야 ID가 정해지므로(IDENTITY) 바로 반영한 뒤 이벤트를 발행한다 (가입 이벤트와 같은 이유)
		Seller savedSeller = sellerRepository.saveAndFlush(
			new Seller(member, sellerType, businessName, businessNumber, representativeName, payoutAccount, now)
		);
		savedSeller.publishChangedEvent();

		// 신청과 같은 트랜잭션에서 지금 판매자 약관에 동의한 기록을 남긴다. 신청이 롤백되면 기록도 함께 사라진다
		// TermsDataInit이 켜질 때 만들어 두므로 없으면 설정 오류다
		Terms sellerTerms = termsRepository.findCurrent(ClockTimes.today(clock)).stream()
			.filter(terms -> terms.getType() == TermsType.SELLER)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("시행 중인 판매자 약관이 없습니다."));
		termsAgreementRepository.save(new TermsAgreement(member, sellerTerms, true, now));
		return savedSeller;
	}
}
