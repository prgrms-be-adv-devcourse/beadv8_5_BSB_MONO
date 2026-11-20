package com.bukang.member.recordcert.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.recordcert.domain.SubmissionStatus;

/**
 * 참가권 환불로 기록증 제출 닫기 (BUK-277, refund-close-spec)
 * - 확인 중 · 심사 필요 · 자동 승인 · 승인은 취소됨으로 닫는다. 반려 · 승인 불가는 그대로 둔다
 * - 제출이 없는 참가권이면 아무것도 하지 않는다
 * - 같은 환불 알림을 두 번 받아도 결과가 같다 (인박스 없이 상태가 막는다)
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class RecordCertRefundCloseTest {
	private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 10, 9, 0);
	private static final LocalDateTime REFUNDED = LocalDateTime.of(2026, 11, 18, 12, 0);
	private static final long REFUNDED_ITEM = 7001;

	@Autowired
	private RecordCertRefundCloseUseCase recordCertRefundCloseUseCase;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void tearDown() {
		jdbcTemplate.update("delete from member_record_cert_submission");
	}

	@Test
	@DisplayName("확인 중인 제출은 환불되면 취소됨으로 닫힌다")
	void closesChecking() {
		long id = submission(REFUNDED_ITEM, SubmissionStatus.CHECKING);

		recordCertRefundCloseUseCase.close(REFUNDED_ITEM, REFUNDED);

		assertThat(status(id)).isEqualTo(SubmissionStatus.CANCELED);
	}

	@Test
	@DisplayName("심사 필요 · 자동 승인 · 승인 제출도 취소됨으로 닫힌다")
	void closesReviewAndApproved() {
		long needsReview = submission(REFUNDED_ITEM, SubmissionStatus.NEEDS_REVIEW);
		long autoApproved = submission(REFUNDED_ITEM, SubmissionStatus.AUTO_APPROVED);
		long approved = submission(REFUNDED_ITEM, SubmissionStatus.APPROVED);

		recordCertRefundCloseUseCase.close(REFUNDED_ITEM, REFUNDED);

		assertThat(status(needsReview)).isEqualTo(SubmissionStatus.CANCELED);
		assertThat(status(autoApproved)).isEqualTo(SubmissionStatus.CANCELED);
		assertThat(status(approved)).isEqualTo(SubmissionStatus.CANCELED);
	}

	@Test
	@DisplayName("반려 · 승인 불가 제출은 환불돼도 그대로 둔다 (실패 사유를 남긴다)")
	void keepsRejectedAndDenied() {
		long rejected = submission(REFUNDED_ITEM, SubmissionStatus.REJECTED);
		long denied = submission(REFUNDED_ITEM, SubmissionStatus.DENIED);

		recordCertRefundCloseUseCase.close(REFUNDED_ITEM, REFUNDED);

		assertThat(status(rejected)).isEqualTo(SubmissionStatus.REJECTED);
		assertThat(status(denied)).isEqualTo(SubmissionStatus.DENIED);
		assertThat(updatedAt(rejected)).isEqualTo(CREATED);
		assertThat(updatedAt(denied)).isEqualTo(CREATED);
	}

	@Test
	@DisplayName("다른 참가권의 제출은 건드리지 않고, 제출이 없는 참가권이면 아무 일도 없이 끝난다")
	void ignoresOtherItemsAndNoSubmission() {
		long otherItem = submission(7002, SubmissionStatus.CHECKING);

		recordCertRefundCloseUseCase.close(REFUNDED_ITEM, REFUNDED);

		assertThat(status(otherItem)).isEqualTo(SubmissionStatus.CHECKING);
	}

	@Test
	@DisplayName("같은 환불 알림을 두 번 받아도 결과가 같다 (처음 닫은 시각을 그대로 둔다)")
	void sameRefundTwice() {
		long checking = submission(REFUNDED_ITEM, SubmissionStatus.CHECKING);
		long denied = submission(REFUNDED_ITEM, SubmissionStatus.DENIED);

		recordCertRefundCloseUseCase.close(REFUNDED_ITEM, REFUNDED);
		recordCertRefundCloseUseCase.close(REFUNDED_ITEM, REFUNDED.plusMinutes(5));

		assertThat(status(checking)).isEqualTo(SubmissionStatus.CANCELED);
		assertThat(updatedAt(checking)).isEqualTo(REFUNDED);
		assertThat(status(denied)).isEqualTo(SubmissionStatus.DENIED);
	}

	private long submission(long orderItemId, SubmissionStatus status) {
		jdbcTemplate.update("""
			insert into member_record_cert_submission (record_cert_id, member_id, order_item_id, race_id,
				race_course_id, seller_member_id, status, version, created_at, updated_at)
			values (1, 1, ?, 1, 1, 9, ?, 0, ?, ?)
			""", orderItemId, status.name(), CREATED, CREATED);
		return jdbcTemplate.queryForObject("select max(submission_id) from member_record_cert_submission", Long.class);
	}

	private SubmissionStatus status(long submissionId) {
		return SubmissionStatus.valueOf(jdbcTemplate.queryForObject(
			"select status from member_record_cert_submission where submission_id = ?", String.class, submissionId));
	}

	private LocalDateTime updatedAt(long submissionId) {
		return jdbcTemplate.queryForObject(
			"select updated_at from member_record_cert_submission where submission_id = ?", LocalDateTime.class,
			submissionId);
	}
}
