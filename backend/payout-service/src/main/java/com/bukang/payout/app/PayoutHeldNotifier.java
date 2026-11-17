package com.bukang.payout.app;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bukang.common.shared.notification.NotificationKafkaSender;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.out.RaceSellerPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 지급보류가 커밋된 뒤 판매자에게 정산 보류 알림(PAYOUT_HELD)을 보낸다 (PR 570 리뷰)
 * 상품 서비스 조회(최대 3초)를 보류 트랜잭션 밖으로 빼서, 정산 내역 잠금을 그동안 쥐지 않는다. 롤백되면 부르지 않는다
 * 커밋 뒤라 EventPublisher로 다시 발행하면 나가지 않으므로 NotificationKafkaSender를 직접 부른다
 * 알림은 잃어도 되는 메시지라(11/04 결정) 판매자를 찾지 못하면 로그만 남긴다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutHeldNotifier {
	private static final String TITLE = "정산이 보류됐어요";
	private static final String HREF = "/seller/settlements";

	private final RaceSellerPort raceSellerPort;
	private final NotificationKafkaSender notificationKafkaSender;

	// shortcut: 커밋 뒤에도 같은 요청 쓰레드에서 돌아 DB 연결은 정리 전까지 쥐고 있다(잠금은 풀림). 길어지면 비동기로 바꾼다
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void notify(PayoutHeldEvent event) {
		Optional<Long> sellerMemberId;
		try {
			sellerMemberId = raceSellerPort.findSellerMemberId(event.raceId());
		} catch (RuntimeException exception) {
			log.warn("판매자 회원을 찾지 못해 정산 보류 알림을 보내지 않았습니다. holdCode={}, raceId={}, cause={}",
				event.holdCode(), event.raceId(), exception.toString());
			return;
		}
		if (sellerMemberId.isEmpty()) {
			log.warn("대회가 없어 정산 보류 알림을 보내지 않았습니다. holdCode={}, raceId={}", event.holdCode(),
				event.raceId());
			return;
		}
		notificationKafkaSender.send(NotificationRequestedEvent.of(sellerMemberId.get(), NotificationKind.PAYOUT_HELD,
			TITLE, body(event), HREF));
	}

	private static String body(PayoutHeldEvent event) {
		String stage = event.payoutStage() == PayoutStage.일차_정산 ? "1차 정산" : "최종 정산";
		String reason = event.reasonType().name().replace('_', ' ');
		return "%s %s의 지급이 멈췄어요.\n사유: %s\n보류가 풀리면 다시 지급 일정이 잡혀요."
			.formatted(event.raceName(), stage, reason);
	}
}
