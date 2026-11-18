package com.bukang.member.recordcert.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.bukang.member.recordcert.domain.exception.SubmissionNotCheckingException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기록증 제출: 참가자가 기록증 하나를 자기 참가권 한 장에 낸 것 (BUK-273)
 * 승인 불가를 받으면 끝나고, 다시 내려면 새 제출을 만든다. 그래서 참가권 ID에 unique를 걸지 않는다
 * 상태 규칙 T1~T5: 반려는 반려, 통과는 서버 확인이 모두 맞을 때만 자동 승인, 나머지는 심사 필요
 */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(
	name = "member_record_cert_submission",
	indexes = {
		@Index(name = "idx_record_cert_submission_status", columnList = "status, created_at"),
		@Index(name = "idx_record_cert_submission_order_item", columnList = "order_item_id"),
		@Index(name = "idx_record_cert_submission_race", columnList = "race_id, status"),
		@Index(name = "idx_record_cert_submission_member", columnList = "member_id")
	}
)
public class RecordCertSubmission {
	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "submission_id")
	private long id;

	@Column(name = "record_cert_id", nullable = false)
	private long recordCertId;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	// 참가권 = order-service의 주문 항목. 환불 이벤트(OrderItemRefundedEvent)를 이 값으로 맞춘다
	@Column(name = "order_item_id", nullable = false)
	private long orderItemId;

	@Column(name = "race_id", nullable = false)
	private long raceId;

	@Column(name = "race_course_id", nullable = false)
	private long raceCourseId;

	// 대회 판매자의 회원 ID. 제출 때 종목 규칙 조회가 돌려준 값으로, 판매자 심사(BUK-276)가 소유 확인 · 건수에 쓴다
	@Column(name = "seller_member_id", nullable = false)
	private long sellerMemberId;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private SubmissionStatus status;

	// 판정 사유를 쉼표로 이어 둔다 (예: TOO_FAST,COURSE_OTHER)
	@Column(name = "judgement_reasons", length = 200)
	private String judgementReasons;

	@Enumerated(STRING)
	@Column(name = "net_time_check", columnDefinition = "varchar(12)")
	private FieldCheck netTimeCheck;

	@Enumerated(STRING)
	@Column(name = "net_label_check", columnDefinition = "varchar(12)")
	private FieldCheck netLabelCheck;

	@Enumerated(STRING)
	@Column(name = "race_date_check", columnDefinition = "varchar(12)")
	private FieldCheck raceDateCheck;

	@Enumerated(STRING)
	@Column(name = "course_check", columnDefinition = "varchar(12)")
	private FieldCheck courseCheck;

	// 낙관적 잠금 (judgement-spec 3장, 11/18). 결과 반영 · 판매자 심사 · 환불 닫기가 같은 제출을 동시에 바꾸면 늦게 저장한 쪽이 실패한다
	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private RecordCertSubmission(long recordCertId, long memberId, long orderItemId, long raceId, long raceCourseId,
		long sellerMemberId, LocalDateTime now) {
		this.recordCertId = recordCertId;
		this.memberId = memberId;
		this.orderItemId = orderItemId;
		this.raceId = raceId;
		this.raceCourseId = raceCourseId;
		this.sellerMemberId = sellerMemberId;
		this.status = SubmissionStatus.CHECKING;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static RecordCertSubmission submit(long recordCertId, long memberId, long orderItemId, long raceId,
		long raceCourseId, long sellerMemberId, LocalDateTime now) {
		return new RecordCertSubmission(recordCertId, memberId, orderItemId, raceId, raceCourseId, sellerMemberId, now);
	}

	// 반려는 서버 확인과 상관없이 반려, 통과는 서버 확인이 모두 맞을 때만 자동 승인 (T1~T4)
	public void applyCheck(Judgement judgement, ServerCheck serverCheck, LocalDateTime now) {
		if (status != SubmissionStatus.CHECKING) {
			throw new SubmissionNotCheckingException();
		}
		this.status = switch (judgement.verdict()) {
			case REJECT -> SubmissionStatus.REJECTED;
			case PASS -> serverCheck.allMatch() ? SubmissionStatus.AUTO_APPROVED : SubmissionStatus.NEEDS_REVIEW;
			case NEEDS_REVIEW -> SubmissionStatus.NEEDS_REVIEW;
		};
		this.judgementReasons = judgement.reasons().stream().map(Enum::name).collect(Collectors.joining(","));
		this.netTimeCheck = serverCheck.results().get(CheckField.NET_TIME);
		this.netLabelCheck = serverCheck.results().get(CheckField.NET_LABEL);
		this.raceDateCheck = serverCheck.results().get(CheckField.RACE_DATE);
		this.courseCheck = serverCheck.results().get(CheckField.COURSE);
		this.updatedAt = now;
	}

	// 서버 확인 결과를 끝내 처리하지 못했을 때(실패 토픽) 판매자가 원본을 보고 정하게 넘긴다. 판정 사유 · 칸별 결과는 비어 있다
	public void giveUpCheck(LocalDateTime now) {
		if (status != SubmissionStatus.CHECKING) {
			throw new SubmissionNotCheckingException();
		}
		this.status = SubmissionStatus.NEEDS_REVIEW;
		this.updatedAt = now;
	}

	public List<JudgementReason> judgementReasons() {
		if (judgementReasons == null || judgementReasons.isEmpty()) {
			return List.of();
		}
		return Arrays.stream(judgementReasons.split(",")).map(JudgementReason::valueOf).toList();
	}

	public ServerCheck serverCheck() {
		Map<CheckField, FieldCheck> results = new EnumMap<>(CheckField.class);
		putIfPresent(results, CheckField.NET_TIME, netTimeCheck);
		putIfPresent(results, CheckField.NET_LABEL, netLabelCheck);
		putIfPresent(results, CheckField.RACE_DATE, raceDateCheck);
		putIfPresent(results, CheckField.COURSE, courseCheck);
		return new ServerCheck(results);
	}

	private static void putIfPresent(Map<CheckField, FieldCheck> results, CheckField field, FieldCheck check) {
		if (check != null) {
			results.put(field, check);
		}
	}
}
