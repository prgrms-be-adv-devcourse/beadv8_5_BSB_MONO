package com.bukang.payout.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.exception.PayoutHoldNotFoundException;
import com.bukang.payout.domain.exception.PayoutNotFoundException;
import com.bukang.payout.dto.PayoutHoldDto;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutRepository;

import lombok.RequiredArgsConstructor;

/**
 * 지급보류 걸기 · 풀기 (payout.md 1장 지급보류, 2-5)
 * 관리자가 거는 보류와, 정산 생성 배치가 만들 때 거는 자동 보류를 모두 여기서 건다
 * 상태 규칙은 Payout이 지키고, 여기서는 찾아서 넘기고 저장만 한다
 * 지급 배치와 동시에 같은 정산 내역을 바꾸면 Payout의 @Version으로 늦게 커밋한 쪽이 실패한다
 */
@Service
@RequiredArgsConstructor
public class PayoutHoldUseCase {
	// 자동 보류의 사유 상세. 관리자가 건 보류와 구분되도록 고정 문구를 남긴다
	private static final String AUTO_HOLD_REASON = "정산 계좌 미등록으로 자동 보류";

	private final PayoutRepository payoutRepository;
	private final PayoutHoldRepository payoutHoldRepository;
	private final BusinessDayCalendar businessDayCalendar;

	// open-in-view가 꺼져 있어 트랜잭션 밖에서 hold.getPayout()을 읽지 않도록, 안에서 DTO로 바꿔 돌려준다
	@Transactional
	public PayoutHoldDto hold(long payoutId, PayoutHoldReason reasonType, String reason, long heldBy,
		LocalDateTime now) {
		Payout payout = payoutRepository.findById(payoutId).orElseThrow(PayoutNotFoundException::new);
		return PayoutHoldDto.from(save(payout.hold(reasonType, reason, heldBy, now)));
	}

	@Transactional
	public PayoutHoldDto release(long holdId, long releasedBy, LocalDateTime now) {
		PayoutHold hold = payoutHoldRepository.findById(holdId).orElseThrow(PayoutHoldNotFoundException::new);
		hold.getPayout().release(hold, releasedBy, now, businessDayCalendar);
		return PayoutHoldDto.from(hold);
	}

	/**
	 * 방금 만든 정산 내역(1차 · 최종)의 자동 보류: 판매자가 정산 계좌를 등록하지 않았으면 건다 (PRO-27)
	 * 1차가 보류 중이어도 최종은 따로 보류하지 않는다. 최종은 1차가 지급된 뒤에만 나가기 때문이다 (PRO-188 · PRO-189)
	 * 정산 내역 · 항목과 함께 롤백되도록 정산 생성 트랜잭션 안에서만 부른다(MANDATORY). 이미 저장된 엔티티를 받으므로
	 * 새 트랜잭션을 열면 정산 내역과 따로 커밋될 수 있다
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void holdNew(Payout payout, boolean hasPayoutAccount, LocalDateTime now) {
		if (!hasPayoutAccount) {
			holdForMissingAccount(payout, now);
		}
	}

	// 계좌가 없으면 지급 배치가 고르지 않도록 HELD로 둔다. 판매자가 계좌를 등록하면 관리자가 보류를 푼다
	private void holdForMissingAccount(Payout payout, LocalDateTime now) {
		holdBySystem(payout, PayoutHoldReason.ACCOUNT_NOT_REGISTERED, AUTO_HOLD_REASON, now);
	}

	// 보류는 정산 내역의 PK를 참조하므로 정산 내역을 저장한 뒤에 건다
	private void holdBySystem(Payout payout, PayoutHoldReason reasonType, String reason, LocalDateTime now) {
		save(payout.hold(reasonType, reason, PayoutHold.SYSTEM_MEMBER_ID, now));
	}

	// 지급보류 ID의 끝 4자리는 PK에서 만들므로 저장해서 ID를 받은 뒤에 붙인다
	private PayoutHold save(PayoutHold hold) {
		payoutHoldRepository.save(hold);
		hold.assignHoldCode();
		return hold;
	}
}
