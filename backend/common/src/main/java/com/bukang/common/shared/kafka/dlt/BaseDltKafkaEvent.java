package com.bukang.common.shared.kafka.dlt;

import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DLT로 옴 → [생성자 + save] 처리 실패 + Slack 알림
 *                 │
 *         사람이 Slack을 보고 원인을 찾음. 고르는 길은 둘
 *                 │
 *    ┌────────────┴────────────────┐
 *    ▼ 길 A: 코드를 고쳐 배포함      ▼ 길 B: 데이터를 손으로 고쳤거나 버릴 메시지
 *  [markResent] 다시 보냄          [completeManually(메모)] 처리 완료
 *    │                                 예: "DB에서 자리 수 직접 수정"
 *    ├ 처리 성공 → [completeByResend] 처리 완료 (자동, 메모 "다시 보내 처리됨")
 *    └ 또 실패  → [markFailedAgain] 처리 실패 + Slack 알림
 *                   → 다시 길 A 또는 길 B를 고름
 *
 * 상속한 엔티티는 @Table에 message_id 유니크 제약을 건다.
 */
@MappedSuperclass
@NoArgsConstructor(access = PROTECTED)
@Getter
public abstract class BaseDltKafkaEvent extends BaseIdAndTime {
	// 예외 메시지 칸 길이. 넘으면 잘라 넣는다
	public static final int EXCEPTION_MESSAGE_MAX_LENGTH = 1000;
	// 다시 보낸 메시지가 처리되어 자동으로 닫을 때 남기는 메모
	public static final String AUTO_COMPLETE_MEMO = "다시 보내 처리됨";

	// 이 행이 어느 서비스의 실패 메시지인지 (예: product). 서비스 엔티티가 정한다
	// Slack 알림 첫 줄([product]), 내부 API 주소(/internal/v1/product/kafka-failures), 오류 메시지에 쓴다
	public abstract String serviceName();

	// 처음 메시지가 온 토픽 (예: queuing.member.joined). 다시 보낼 토픽을 이 값으로 만든다
	@Column(name = "original_topic", nullable = false, length = 100)
	private String originalTopic;

	// 아래 세 칸은 마지막으로 실패한 메시지가 들어 있는 DLT의 위치다. Kafka 콘솔(http://localhost:8091)에서
	// 토픽 → 파티션 → 오프셋 순으로 메시지를 찾아가는 확인용 정보이고, 처리·다시 보내기·같은 메시지 판단에는 쓰지 않는다.
	// 다시 보낸 메시지가 또 실패하면 DLT의 새 위치로 들어오므로, 알림을 받고 바로 찾아가도록 마지막 위치로 바꾼다.

	// DLT 토픽 이름 = 원래 토픽 + 서비스별 DLT 접미사 (예: queuing.member.joined.product.dlt)
	@Column(name = "dlt_topic", nullable = false, length = 100)
	private String dltTopic;

	// DLT 토픽 안의 파티션 번호. 오프셋은 파티션마다 0부터 따로 세므로 오프셋만으로는 메시지를 찾을 수 없다
	// (파티션 0의 7번과 파티션 1의 7번은 다른 메시지). 지금은 토픽마다 파티션이 1개라 늘 0이다
	@Column(name = "dlt_partition", nullable = false)
	private int dltPartition;

	// 파티션 안에서 몇 번째로 들어온 메시지인지 (0부터). Kafka가 붙이는 위치 번호이고, message-id와는 관계없다
	@Column(name = "dlt_offset", nullable = false)
	private long dltOffset;

	// 보낸 쪽이 붙인 메시지 키. 다시 보낼 때 같은 키로 보내 같은 파티션에 넣는다
	@Column(name = "message_key", length = 100)
	private String messageKey;

	// 메시지 본문(JSON)
	@Column(name = "payload", nullable = false, columnDefinition = "TEXT")
	private String payload;

	// message-id 헤더 값 (예: member-outbox-{UUID}, 보낸 서비스가 아웃박스 행을 만들 때 정한 값). 서비스 엔티티가 유니크 제약을 건다
	// 다시 보낼 때 그대로 붙여 이미 처리한 메시지면 인박스가 건너뛴다. 헤더 없이 온 메시지는 null
	// (MySQL 유니크 제약은 null끼리 겹치는 것으로 보지 않아 null 행은 여러 개 생길 수 있다)
	@Column(name = "message_id", length = 100)
	private String messageId;

	// 마지막 실패의 예외 종류와 메시지. 다시 보낸 메시지가 또 실패하면 그 예외로 덮어쓴다
	@Column(name = "exception_class", length = 255)
	private String exceptionClass;

	@Column(name = "exception_message", length = EXCEPTION_MESSAGE_MAX_LENGTH)
	private String exceptionMessage;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private DltKafkaEventStatus status;

	// 마지막으로 다시 보낸 시각
	// 알림 시각은 따로 두지 않는다. 실패할 때마다 알리므로 처음 알림은 create_date, 마지막 알림은 modify_date와 같다
	@Column(name = "resent_date")
	private LocalDateTime resentDate;

	// 처리 완료로 닫은 시각과 사유
	@Column(name = "completed_date")
	private LocalDateTime completedDate;

	@Column(name = "complete_memo", length = 500)
	private String completeMemo;

	// DLT로 처음 온 메시지를 처리 실패 행으로 만든다. 서비스 엔티티의 생성자가 부르고, 서비스 저장소의 save로 넣는다
	// 값은 KafkaDltMessage.from이 꺼낸 그대로 쓴다 (예외 메시지는 거기서 이미 칸 길이에 맞게 잘랐다)
	protected BaseDltKafkaEvent(KafkaDltMessage message) {
		this.originalTopic = message.originalTopic();
		this.dltTopic = message.dltTopic();
		this.dltPartition = message.dltPartition();
		this.dltOffset = message.dltOffset();
		this.messageKey = message.messageKey();
		this.payload = message.payload();
		this.messageId = message.messageId();
		this.exceptionClass = message.exceptionClass();
		this.exceptionMessage = message.exceptionMessage();
		this.status = DltKafkaEventStatus.FAILED;
	}

	// 다시 보내기 직전에 부른다. 처리 실패 상태에서만 다시 보낼 수 있다
	// (다시 보냄이면 이미 보낸 메시지의 결과를 기다리는 중이고, 처리 완료면 끝난 메시지다)
	// 보내기 전에 이 상태를 먼저 커밋한다. 보낸 메시지를 본 리스너가 처리했을 때 이 행이 다시 보냄이어야 처리 완료로 바뀐다
	public void markResent(LocalDateTime now) {
		if (status != DltKafkaEventStatus.FAILED) {
			throw new DltKafkaEventStatusException(serviceName(), getId(), status, "다시 보낼");
		}
		status = DltKafkaEventStatus.RESENT;
		resentDate = now;
	}

	// 다시 보냄으로 바꿨지만 Kafka로 보내지 못했을 때 부른다. 처리 실패로 되돌려 다시 보낼 수 있게 한다
	// resent_date는 보내려 한 시각으로 남는다
	public void cancelResent() {
		if (status == DltKafkaEventStatus.RESENT) {
			status = DltKafkaEventStatus.FAILED;
		}
	}

	// 다시 보낸 메시지가 또 실패해 DLT로 오면 부른다. 처리 실패로 되돌렸으면 true를 돌려주고, 그때만 Slack으로 알린다
	// 다시 보냄이 아니면(같은 DLT 메시지를 두 번 받아 이미 처리 실패 등) 바꾸지 않고 false를 돌려준다
	// DLT 리스너에서 예외가 나면 기본 오류 처리기가 같은 메시지를 10번 다시 시도한 뒤 에러 로그를 남긴다.
	public boolean markFailedAgain(String dltTopic, int dltPartition, long dltOffset,
		String exceptionClass, String exceptionMessage) {
		if (status != DltKafkaEventStatus.RESENT) {
			return false;
		}
		this.dltTopic = dltTopic;
		this.dltPartition = dltPartition;
		this.dltOffset = dltOffset;
		this.exceptionClass = exceptionClass;
		this.exceptionMessage = truncateExceptionMessage(exceptionMessage);
		status = DltKafkaEventStatus.FAILED;
		return true;
	}

	// 다시 보낸 메시지를 처리했을 때 본 리스너가 부른다. 다시 보냄이 아니면 바꾸지 않고 false를 돌려준다
	// 본 리스너의 처리를 롤백시키지 않도록 예외를 내지 않는다
	public boolean completeByResend(LocalDateTime now) {
		if (status != DltKafkaEventStatus.RESENT) {
			return false;
		}
		complete(now, AUTO_COMPLETE_MEMO);
		return true;
	}

	// 사람이 원인을 고친 뒤 내부 API로 닫는다. 메모는 필수이고, 이미 처리 완료면 거절한다
	public void completeManually(LocalDateTime now, String memo) {
		if (status == DltKafkaEventStatus.COMPLETED) {
			throw new DltKafkaEventStatusException(serviceName(), getId(), status, "처리 완료로 바꿀");
		}
		if (memo == null || memo.isBlank()) {
			throw new IllegalArgumentException("처리 완료 메모를 적어야 합니다.");
		}
		complete(now, memo.strip());
	}

	// 예외 메시지를 칸 길이에 맞게 자른다. 넘는 값을 넣으면 DB가 거절해 실패 기록 자체가 남지 않는다
	// KafkaDltMessage.from(처음 행)과 markFailedAgain(다시 실패)에서 쓴다
	public static String truncateExceptionMessage(String message) {
		if (message == null || message.length() <= EXCEPTION_MESSAGE_MAX_LENGTH) {
			return message;
		}
		return message.substring(0, EXCEPTION_MESSAGE_MAX_LENGTH);
	}

	private void complete(LocalDateTime now, String memo) {
		status = DltKafkaEventStatus.COMPLETED;
		completedDate = now;
		completeMemo = memo;
	}
}
