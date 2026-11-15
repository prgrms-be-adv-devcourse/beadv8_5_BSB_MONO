package com.bukang.payout.in;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.cash.event.ClaimDepositedEvent;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.order.event.OrderItemRefundedEvent;
import com.bukang.common.shared.race.event.RaceCancelEvent;
import com.bukang.payout.app.PayoutClaimUseCase;
import com.bukang.payout.app.PayoutDltFacade;
import com.bukang.payout.app.PayoutKafkaRetry;
import com.bukang.payout.app.PayoutRaceCancelUseCase;
import com.bukang.payout.app.PayoutRefundUseCase;
import com.bukang.payout.domain.exception.PayoutClaimConflictException;
import com.bukang.payout.domain.exception.PayoutClaimNotFoundException;
import com.bukang.payout.domain.exception.PayoutRecoveryConflictException;

import lombok.RequiredArgsConstructor;

/**
 * 다른 서비스가 보낸 Kafka 메시지를 받는다. 메시지는 JSON 문자열이고 JsonConverter로 바꾼다
 * 처리에 실패하면 재시도 토픽으로 보내 간격을 늘려 가며 다시 하고, 그래도 실패하면 <토픽>.payout.dlt로 보낸다
 * DLT 메시지는 PayoutKafkaDltConsumer가 실패 테이블에 남긴다. 다시 보낸 메시지를 처리하면 그 행을 처리 완료로 닫는다
 */
@Component
@RequiredArgsConstructor
public class PayoutKafkaConsumer {
	// 주문 서비스(group "order")와 groupId가 달라, 같은 대회 취소 메시지를 정산도 따로 받는다
	private static final String GROUP_ID = "payout";
	// 재시도·DLT 토픽 파티션 수. 대회 취소는 드물어 1개로 충분하다
	private static final String PARTITIONS = "1";
	// 처음 시도를 포함한 시도 횟수. 지급 배치와 같은 정산 내역을 동시에 바꿔 낙관적 락이 실패해도 다음 시도에서 걸린다
	private static final String RETRY_ATTEMPTS = "4";
	// 첫 재시도 지연과 재시도·DLT 토픽 접미사는 PayoutKafkaRetry에 있다. 다시 보내기(PayoutDltUseCase)가 같은 값으로
	// 첫 재시도 토픽 이름(예: order.race.cancel.payout.retry-10000)을 만든다
	private static final long RETRY_DELAY_MILLIS = PayoutKafkaRetry.FIRST_RETRY_DELAY_MILLIS;
	private static final double RETRY_MULTIPLIER = 3.0;
	private static final String RETRY_TOPIC_SUFFIX = PayoutKafkaRetry.RETRY_TOPIC_SUFFIX;
	private static final String DLT_TOPIC_SUFFIX = PayoutKafkaRetry.DLT_TOPIC_SUFFIX;
	// DLT는 PayoutKafkaDltConsumer가 받으므로, @RetryableTopic이 기본으로 띄우는 DLT 리스너는 시작하지 않는다
	// (켜 두면 같은 DLT 메시지를 두 곳에서 받는다)
	private static final String AUTO_START_DEFAULT_DLT_HANDLER = "false";

	// 서버 시간대와 상관없이 한국 시간으로 상태 이력 · 회수 요청 시각을 남긴다 (다른 정산 진입점과 같은 기준)
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private final PayoutRaceCancelUseCase payoutRaceCancelUseCase;
	private final PayoutRefundUseCase payoutRefundUseCase;
	private final PayoutClaimUseCase payoutClaimUseCase;
	private final PayoutDltFacade payoutDltFacade;
	private final JsonConverter jsonConverter;

	// 판매자가 대회를 취소했다 (상품 서비스가 발행, key는 대회 ID). 지급 전 정산 내역은 닫고, 지급한 돈은 회수를 만든다
	// 이미 닫은 정산 내역과 이미 만든 회수는 건너뛰므로 같은 메시지를 다시 처리해도 안전하다
	@KafkaListener(topics = KafkaTopics.ORDER_RACE_CANCEL, groupId = GROUP_ID)
	@RetryableTopic(
		attempts = RETRY_ATTEMPTS,
		backOff = @BackOff(delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER),
		// 깨진 JSON은 다시 해도 실패하므로 재시도 없이 바로 DLT로 보낸다 (JsonConverter가 IllegalArgumentException으로 바꾼다)
		exclude = IllegalArgumentException.class,
		numPartitions = PARTITIONS,
		retryTopicSuffix = RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = DLT_TOPIC_SUFFIX,
		autoStartDltHandler = AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleRaceCancel(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		RaceCancelEvent event = jsonConverter.fromJson(payload, RaceCancelEvent.class);
		payoutRaceCancelUseCase.cancelRace(event.raceId(), LocalDateTime.now(ZONE));
		payoutDltFacade.completeByResend(messageId);
	}

	// 구매자에게 참가권을 환불했다 (주문 서비스가 발행, key는 주문 상품 ID). 1차 정산을 받은 참가권이면 회수를 남긴다
	// 같은 참가권으로 다시 와도 이미 만든 회수를 돌려주므로 같은 메시지를 다시 처리해도 안전하다
	@KafkaListener(topics = KafkaTopics.ORDER_ITEM_REFUNDED, groupId = GROUP_ID)
	@RetryableTopic(
		attempts = RETRY_ATTEMPTS,
		backOff = @BackOff(delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER),
		// 다시 해도 결과가 같은 실패는 재시도 없이 바로 DLT로 보낸다
		// 깨진 JSON · 사유 없는 알림(IllegalArgumentException), 최종 정산 뒤 환불(PayoutRecoveryConflictException)
		exclude = {IllegalArgumentException.class, PayoutRecoveryConflictException.class},
		numPartitions = PARTITIONS,
		retryTopicSuffix = RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = DLT_TOPIC_SUFFIX,
		autoStartDltHandler = AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleItemRefunded(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		OrderItemRefundedEvent event = jsonConverter.fromJson(payload, OrderItemRefundedEvent.class);
		payoutRefundUseCase.recordRefund(event, LocalDateTime.now(ZONE));
		payoutDltFacade.completeByResend(messageId);
	}

	// 판매자가 청구 전용 가상계좌에 입금했다 (캐시 서비스가 발행, key는 청구 ID, BUK-88). 회수를 끝낸다
	// 이미 확인한 청구는 건너뛰므로 같은 메시지를 다시 처리해도 안전하다
	@KafkaListener(topics = KafkaTopics.CLAIM_DEPOSITED, groupId = GROUP_ID)
	@RetryableTopic(
		attempts = RETRY_ATTEMPTS,
		backOff = @BackOff(delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER),
		// 다시 해도 결과가 같은 실패는 재시도 없이 바로 DLT로 보낸다
		// 깨진 JSON(IllegalArgumentException), 없는 청구, 입금액 · 갚을 돈이 청구 금액과 다름(PayoutClaimConflictException)
		exclude = {IllegalArgumentException.class, PayoutClaimNotFoundException.class,
			PayoutClaimConflictException.class},
		numPartitions = PARTITIONS,
		retryTopicSuffix = RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = DLT_TOPIC_SUFFIX,
		autoStartDltHandler = AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleClaimDeposited(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		ClaimDepositedEvent event = jsonConverter.fromJson(payload, ClaimDepositedEvent.class);
		payoutClaimUseCase.confirmByDeposit(event.claimCode(), event.amount(), event.depositedAt());
		payoutDltFacade.completeByResend(messageId);
	}
}
