package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bukang.common.shared.notification.NotificationKafkaSender;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutStage;

class PayoutHeldNotifierTest {
	private static final PayoutHeldEvent HELD = new PayoutHeldEvent(10, "테스트 대회", PayoutStage.최종_정산,
		PayoutHoldReason.정산_계좌_미등록, "HD-20261012-0001");

	private final NotificationKafkaSender sender = mock(NotificationKafkaSender.class);

	@Test
	@DisplayName("대회 판매자의 회원 ID로 정산 보류 알림을 보낸다")
	void sendsToSellerMember() {
		new PayoutHeldNotifier(raceId -> raceId == 10 ? Optional.of(500L) : Optional.empty(), sender).notify(HELD);

		ArgumentCaptor<NotificationRequestedEvent> sent = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
		verify(sender).send(sent.capture());
		assertThat(sent.getValue().memberId()).isEqualTo(500);
		assertThat(sent.getValue().kind()).isEqualTo(NotificationKind.PAYOUT_HELD);
		assertThat(sent.getValue().body()).contains("테스트 대회", "최종 정산", "정산 계좌 미등록");
		assertThat(sent.getValue().href()).isEqualTo("/seller/settlements");
	}

	@Test
	@DisplayName("상품 서비스가 실패하거나 대회가 없으면 예외 없이 알림만 건너뛴다")
	void skipsWhenSellerUnknown() {
		new PayoutHeldNotifier(raceId -> {
			throw new IllegalStateException("상품 서비스 응답 없음");
		}, sender).notify(HELD);
		new PayoutHeldNotifier(raceId -> Optional.empty(), sender).notify(HELD);

		verify(sender, never()).send(any());
	}
}
