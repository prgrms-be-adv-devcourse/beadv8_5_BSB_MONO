package com.bukang.payout.app;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.exception.PayoutHoldNotFoundException;
import com.bukang.payout.domain.exception.PayoutNotFoundException;
import com.bukang.payout.dto.PayoutDto;
import com.bukang.payout.dto.PayoutHoldDto;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.RaceSellerPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 지급보류 걸기 · 풀기 (payout.md 1장 지급보류, 2-5)
 * 관리자가 거는 보류와, 정산 생성 배치가 만들 때 거는 자동 보류를 모두 여기서 건다
 * 상태 규칙은 Payout이 지키고, 여기서는 찾아서 넘기고 저장만 한다
 * 지급 배치와 동시에 같은 정산 내역을 바꾸면 Payout의 @Version으로 늦게 커밋한 쪽이 실패한다
 * 보류를 새로 걸면 판매자에게 정산 보류 알림(PAYOUT_HELD)을 보낸다. 보류와 같은 트랜잭션에서 발행해 롤백되면 보내지 않는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutHoldUseCase {
	// 자동 보류의 사유 상세. 관리자가 건 보류와 구분되도록 고정 문구를 남긴다
	private static final String AUTO_HOLD_REASON = "정산 계좌 미등록으로 자동 보류";
	private static final String RECONCILE_HOLD_REASON = "정산 대사에서 정산이 판매보다 많아 자동 보류";
	private static final String HELD_NOTICE_TITLE = "정산이 보류됐어요";
	private static final String HELD_NOTICE_HREF = "/seller/settlements";

	private final PayoutRepository payoutRepository;
	private final PayoutHoldRepository payoutHoldRepository;
	private final BusinessDayCalendar businessDayCalendar;
	private final RaceSellerPort raceSellerPort;
	private final EventPublisher eventPublisher;

	// open-in-view가 꺼져 있어 트랜잭션 밖에서 hold.getPayout()을 읽지 않도록, 안에서 DTO로 바꿔 돌려준다
	@Transactional
	public PayoutHoldDto hold(long payoutId, PayoutHoldReason reasonType, String reason, long heldBy,
		LocalDateTime now) {
		Payout payout = payoutRepository.findByIdForUpdate(payoutId).orElseThrow(PayoutNotFoundException::new);
		return PayoutHoldDto.from(save(payout.hold(reasonType, reason, heldBy, now)));
	}

	// 보류 걸 정산 내역을 정산 ID(ST-)로 찾는다 (BUK-109). 응답의 id로 hold()를 부른다
	// 저장된 정산 ID는 대문자라, 청구 ID 검색처럼 앞뒤 공백을 지우고 대문자로 맞춰 찾는다
	@Transactional(readOnly = true)
	public PayoutDto findPayout(String payoutCode) {
		String code = payoutCode.strip().toUpperCase(Locale.ROOT);
		return PayoutDto.from(payoutRepository.findByPayoutCode(code).orElseThrow(PayoutNotFoundException::new));
	}

	// 잠금 쿼리는 보류 ID로 찾으려고 보류 표를 함께 읽는데, Hibernate가 정산 내역만 잠가(for update of) 보류 표는 일반 조회가 된다
	// REPEATABLE READ면 그 일반 조회가 잠금을 기다리기 전에 스냅숏을 잡아, 잠금을 얻은 뒤에도 동시에 풀린 보류를 걸린 것으로 본다.
	// 그래서 READ COMMITTED로 돌려 잠금 뒤의 읽기가 먼저 커밋한 해제를 보게 한다 (BUK-242, MySQL 동시성 테스트로 확인)
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public PayoutHoldDto release(long holdId, long releasedBy, LocalDateTime now) {
		// 정산 내역부터 잠그고, 보류는 잠금 뒤에 읽는다. 같은 보류를 두 번 풀어도 늦은 쪽이 이미 풀린 상태를 본다
		Payout payout = payoutRepository.findByHoldIdForUpdate(holdId).orElseThrow(PayoutHoldNotFoundException::new);
		PayoutHold hold = payoutHoldRepository.findById(holdId).orElseThrow(PayoutHoldNotFoundException::new);
		payout.release(hold, releasedBy, now, businessDayCalendar);
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

	/**
	 * 정산 대사가 정산 쪽이 판매보다 많은 어긋남을 찾으면 건다 (AC-67-13). 돈이 더 나가지 않게 사람이 확인할 때까지 막는다
	 * 지급 전이 아니거나 이미 같은 사유로 보류 중이면 걸지 않는다. 대사 트랜잭션 안에서만 부른다(MANDATORY)
	 * @return 보류를 걸었으면 true
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean holdForReconcileMismatch(Payout payout, LocalDateTime now) {
		if (!payout.getStatus().isBeforePayment() || payout.isHeldFor(PayoutHoldReason.대사_불일치)) {
			return false;
		}
		holdBySystem(payout, PayoutHoldReason.대사_불일치, RECONCILE_HOLD_REASON, now);
		return true;
	}

	// 계좌가 없으면 지급 배치가 고르지 않도록 지급보류로 둔다. 판매자가 계좌를 등록하면 관리자가 보류를 푼다
	private void holdForMissingAccount(Payout payout, LocalDateTime now) {
		holdBySystem(payout, PayoutHoldReason.정산_계좌_미등록, AUTO_HOLD_REASON, now);
	}

	// 보류는 정산 내역의 PK를 참조하므로 정산 내역을 저장한 뒤에 건다
	private void holdBySystem(Payout payout, PayoutHoldReason reasonType, String reason, LocalDateTime now) {
		save(payout.hold(reasonType, reason, PayoutHold.SYSTEM_MEMBER_ID, now));
	}

	// 지급보류 ID의 끝 4자리는 PK에서 만들므로 저장해서 ID를 받은 뒤에 붙인다
	// 보류 기록은 새로 걸 때만 만들어지므로(같은 사유가 걸려 있으면 Payout.hold가 막는다) 여기서 알리면 한 번만 간다
	private PayoutHold save(PayoutHold hold) {
		payoutHoldRepository.save(hold);
		hold.assignHoldCode();
		notifyHeld(hold);
		return hold;
	}

	// 알림은 잃어도 되는 메시지라(11/04 결정) 판매자를 찾지 못해도 보류는 그대로 건다
	private void notifyHeld(PayoutHold hold) {
		Payout payout = hold.getPayout();
		Optional<Long> sellerMemberId;
		try {
			sellerMemberId = raceSellerPort.findSellerMemberId(payout.getRaceId());
		} catch (RuntimeException exception) {
			log.warn("판매자 회원을 찾지 못해 정산 보류 알림을 보내지 않았습니다. holdCode={}, raceId={}, cause={}",
				hold.getHoldCode(), payout.getRaceId(), exception.toString());
			return;
		}
		if (sellerMemberId.isEmpty()) {
			log.warn("대회가 없어 정산 보류 알림을 보내지 않았습니다. holdCode={}, raceId={}", hold.getHoldCode(),
				payout.getRaceId());
			return;
		}
		eventPublisher.publish(NotificationRequestedEvent.of(sellerMemberId.get(), NotificationKind.PAYOUT_HELD,
			HELD_NOTICE_TITLE, heldNoticeBody(payout, hold), HELD_NOTICE_HREF));
	}

	private static String heldNoticeBody(Payout payout, PayoutHold hold) {
		String stage = payout.getPayoutStage() == PayoutStage.일차_정산 ? "1차 정산" : "최종 정산";
		String reason = hold.getReasonType().name().replace('_', ' ');
		return "%s %s의 지급이 멈췄어요.\n사유: %s\n보류가 풀리면 다시 지급 일정이 잡혀요."
			.formatted(payout.getRaceName(), stage, reason);
	}
}
