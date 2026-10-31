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
	// 판매자 정보가 바뀌었다 (등록 신청, 승인·반려). 상품 서비스가 판매자 복제본에 저장한다
	public static final String SELLER_CHANGED = "queuing.seller.changed";
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

	private KafkaTopics() {
	}
}
