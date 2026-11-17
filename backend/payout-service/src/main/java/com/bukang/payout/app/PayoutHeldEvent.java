package com.bukang.payout.app;

import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutStage;

/**
 * 지급보류가 새로 걸렸다 (정산 안에서만 쓰는 이벤트). PayoutHoldUseCase가 보류 트랜잭션 안에서 발행하고,
 * PayoutHeldNotifier가 커밋된 뒤 받아 판매자에게 알린다. 알림 문구에 필요한 값을 담아 커밋 뒤에 DB를 다시 읽지 않는다
 */
public record PayoutHeldEvent(long raceId, String raceName, PayoutStage payoutStage, PayoutHoldReason reasonType,
	String holdCode) {
}
