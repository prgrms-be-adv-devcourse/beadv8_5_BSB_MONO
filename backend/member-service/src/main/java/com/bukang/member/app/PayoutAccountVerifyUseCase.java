package com.bukang.member.app;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.bukang.member.domain.Member;
import com.bukang.member.domain.PayoutAccountHolderType;
import com.bukang.member.domain.VerifiedPayoutAccount;
import com.bukang.member.domain.exception.MemberNotFoundException;
import com.bukang.member.domain.exception.SellerAccountNotVerifiedException;
import com.bukang.member.domain.exception.SellerAccountVerifyTooManyRequestsException;
import com.bukang.member.domain.exception.SellerAccountVerifyUnavailableException;
import com.bukang.member.domain.exception.SellerAlreadyAppliedException;
import com.bukang.member.dto.SellerApplyRequestDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.SellerRepository;
import com.bukang.member.out.payoutaccount.PayoutAccountInquiry;
import com.bukang.member.out.payoutaccount.PayoutAccountVerifier;
import com.bukang.member.out.payoutaccount.PayoutAccountVerifyLimiter;

import lombok.RequiredArgsConstructor;

/**
 * 판매자 신청의 정산 계좌가 신청자의 것인지 확인한다 (BE-031 결정 3~5)
 * DB 트랜잭션 밖에서 부른다. 오픈뱅킹을 기다리는 동안 회원 행 락을 쥐지 않게 하려는 것이다
 * 신청할 수 없는 회원은 오픈뱅킹을 부르기 전에 막아, 조회 횟수를 쓰지 않게 한다. 저장할 때 락을 잡고 한 번 더 확인한다
 */
@Service
@RequiredArgsConstructor
public class PayoutAccountVerifyUseCase {
	private static final DateTimeFormatter BIRTH_DATE = DateTimeFormatter.ofPattern("yyMMdd");

	private final MemberRepository memberRepository;
	private final SellerRepository sellerRepository;
	private final PayoutAccountVerifyLimiter limiter;
	private final PayoutAccountVerifier verifier;

	public VerifiedPayoutAccount verify(long memberId, SellerApplyRequestDto request, LocalDateTime now) {
		Member member = memberRepository.findActiveById(memberId)
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."));
		if (sellerRepository.existsByMemberIdAndStatusIn(memberId, SellerApplyUseCase.ACTIVE_STATUSES)) {
			throw new SellerAlreadyAppliedException("이미 심사 중이거나 승인된 판매자 신청이 있습니다.");
		}
		if (!limiter.tryAcquire(memberId)) {
			throw new SellerAccountVerifyTooManyRequestsException();
		}

		// 사업자 명의는 신청서의 사업자등록번호로, 본인 명의는 본인인증 생년월일로 묻는다. 신청자가 남의 계좌를 넣지 못한다
		boolean business = request.getAccountHolderType() == PayoutAccountHolderType.BUSINESS;
		String holderName;
		try {
			holderName = verifier.verify(new PayoutAccountInquiry(
				request.getBankCode(),
				request.getAccountNumber(),
				request.getAccountHolderType(),
				business ? request.getBusinessNumber().replace("-", "") : member.getBirthDate().format(BIRTH_DATE),
				business ? request.getBusinessName() : member.getName()
			));
		} catch (SellerAccountVerifyUnavailableException exception) {
			// 은행 답을 받지 못했으니 예금주 정보도 새지 않았다. 장애로 하루 횟수를 다 쓰지 않게 돌려준다
			limiter.release(memberId);
			throw exception;
		}
		if (!business && !sameName(holderName, member.getName())) {
			throw new SellerAccountNotVerifiedException();
		}
		return new VerifiedPayoutAccount(request.getBankCode(), request.getAccountNumber(), holderName,
			request.getAccountHolderType(), now);
	}

	// 은행마다 예금주 이름의 띄어쓰기가 달라 공백을 빼고 비교한다
	private static boolean sameName(String holderName, String memberName) {
		return holderName != null && memberName != null
			&& holderName.replaceAll("\\s", "").equals(memberName.replaceAll("\\s", ""));
	}
}
