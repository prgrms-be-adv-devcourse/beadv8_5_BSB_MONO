package com.bukang.common.shared.kafka;

/**
 * Kafka 토픽 이름을 모두 이곳에 모은다 (생산자와 소비자가 같은 상수를 쓴다)
 * 같은 이벤트는 토픽 하나로 보내고, 받는 서비스마다 groupId를 달리해 구독한다 (그룹마다 모든 메시지를 받는다).
 * 회원·판매자·대회·종목 토픽은 queuing.<도메인>.<이벤트>(.claude/rules/kafka-topic-convention.md)로 맞췄고, 나머지는 쓰던 이름 그대로다.
 * 공용 토픽을 @RetryableTopic으로 받는 서비스는 재시도·DLT 토픽 접미사에 서비스 이름을 넣어, 다른 서비스와 겹치지 않게 한다.
 */
public final class KafkaTopics {
	// 회원 이벤트 (member-service가 발행하고 캐시·상품 서비스가 구독한다)
	public static final String MEMBER_MEMBER_JOINED = "queuing.member.joined";
	public static final String MEMBER_MODIFIED = "queuing.member.modified";
	// 회원이 탈퇴했다. 정보 변경 이벤트(닉네임 "탈퇴한 회원")와 함께 보내, 받는 쪽이 닉네임을 비교하지 않고 탈퇴를 안다
	public static final String MEMBER_WITHDRAWN = "queuing.member.withdrawn";
	// 회원의 마케팅 수신 동의가 바뀌었다
	public static final String MARKETING_CONSENT_CHANGED = "queuing.member.consentchanged";
	// 판매자 정보가 바뀌었다 (등록 신청, 승인·반려). 상품 서비스가 판매자 복제본에 저장한다
	public static final String SELLER_CHANGED = "queuing.seller.changed";
	// 크루원이 크루에서 빠졌다 (떠나기·내보내기·없애기·회원 탈퇴). 주문 서비스가 그 사람을 파티에서 뺀다
	public static final String CREW_MEMBER_LEFT = "queuing.crew.memberleft";
	// 회원이 초대 링크로 크루에 가입했다. 알림 서비스가 받는다 (BUK-99)
	public static final String CREW_MEMBER_JOINED = "queuing.crew.memberjoined";
	// 크루장이 바뀌었다 (크루장 넘기기). 알림 서비스가 받는다 (BUK-99)
	public static final String CREW_LEADER_CHANGED = "queuing.crew.leaderchanged";
	// 대회가 바뀌었다 (등록·수정·취소, 상태 배치의 판매 상태 변경). 대기열 서비스가 대회 복제본에 저장한다
	public static final String RACE_CHANGED = "queuing.race.changed";
	// 종목이 바뀌었다 (등록한 종목, 수정 때 바뀐·새·뺀 종목). 대기열 서비스가 종목 복제본에 저장한다
	public static final String RACE_COURSE_CHANGED = "queuing.racecourse.changed";

	// 대회·종목을 저장한 뒤 이미지 연결을 요청한다 (product → file)
	public static final String RACE_FILE_LINK_REQUESTED = "race.filelink.requested";
	// 대기열 활성 시간이 끝나 임시 확보한 자리를 해제한다 (order → product)
	public static final String PRODUCT_HOLD_CANCEL = "product.hold.cancel";
	// 결제한 참가권을 취소·만료해 판매한 자리를 정원으로 되돌린다 (order → product)
	public static final String PRODUCT_HOLD_RESTORE = "product.hold.restore";
	// 판매자가 대회를 취소했다. 주문 서비스가 참가권을 일괄 환불한다 (product → order)
	public static final String ORDER_RACE_CANCEL = "order.race.cancel";
	// 참가권을 환불했다. 정산 서비스가 판매자에게서 돌려받을 회수를 남긴다 (order → payout, 발행은 order 담당과 맞춘다)
	public static final String ORDER_ITEM_REFUNDED = "queuing.orderitem.refunded";
	// 대회 취소 환불의 보류(HELD)·완료(REFUNDED) (order → payout, key 대회 ID)
	// 정산은 완료를 받은 대회만 대회 취소 청구를 만든다. 보류에 지급을 막는 일은 아직 없다(BUK-66).
	// 회수는 참가권마다 ORDER_ITEM_REFUNDED로 한다(BUK-76)
	// 이름은 정산 담당과 확인 중이다 (11/05 회의 R1)
	public static final String ORDER_RACE_REFUND = "queuing.order.racerefund";

	// 판매자가 청구 전용 가상계좌에 입금했다. 정산이 청구를 입금 확인으로 바꾸고 회수를 끝낸다 (cash → payout, key 청구 ID)
	// 이름 · 내용은 캐시 담당과 맞추는 중이다 (BUK-88 초안)
	public static final String CLAIM_DEPOSITED = "queuing.claim.deposited";

	// 회원 한 명에게 알림을 띄워 달라는 요청 (모든 서비스 → notification). 문구는 보내는 쪽이 만든다 (BUK-69)
	public static final String NOTIFICATION_REQUESTED = "push.notification.requested";

	private KafkaTopics() {
	}
}
