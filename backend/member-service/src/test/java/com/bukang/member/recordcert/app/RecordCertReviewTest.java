package com.bukang.member.recordcert.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.recordcert.domain.DenyReason;
import com.bukang.member.recordcert.domain.RecordCertSubmission;
import com.bukang.member.recordcert.domain.SubmissionStatus;
import com.bukang.member.recordcert.domain.exception.RecordCertNotOwnerException;
import com.bukang.member.recordcert.domain.exception.RecordCertNotSellerException;
import com.bukang.member.recordcert.domain.exception.SubmissionNotReviewableException;
import com.bukang.member.support.RecordCertSellerFixture;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 판매자 기록증 심사 (BUK-276)
 * - 심사 필요인 제출만 승인 · 승인 불가로 정한다. 아니면 409
 * - 승인 불가는 사유를 목록에서 고르고 메모(200자 이하)를 선택으로 더한다
 * - 심사를 읽은 뒤 다른 변경(환불 닫기)이 먼저 저장되면 낙관적 잠금으로 409 (11/18)
 * - 승인된 판매자이고, 제출에 저장된 판매자 회원 ID가 로그인 회원이어야 한다. 아니면 403
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({InMemoryIdentityVerificationStore.Config.class, RecordCertSellerFixture.class})
class RecordCertReviewTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 14, 0);

	@Autowired
	private RecordCertReviewUseCase recordCertReviewUseCase;
	@Autowired
	private RecordCertSellerFixture fixture;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@PersistenceContext
	private EntityManager entityManager;

	private long sellerId;

	@BeforeEach
	void setUp() {
		sellerId = fixture.approvedSeller();
	}

	@AfterEach
	void tearDown() {
		fixture.clear();
	}

	@Test
	@DisplayName("심사 필요인 내 대회 제출을 승인하면 승인이 되고 심사 시각이 남는다")
	void approvesNeedsReview() {
		long submissionId = fixture.submission(sellerId, 1, SubmissionStatus.NEEDS_REVIEW);

		recordCertReviewUseCase.approve(sellerId, submissionId, NOW);

		assertThat(jdbcTemplate.queryForMap(
			"select status, reviewed_at from member_record_cert_submission where submission_id = ?", submissionId))
			.containsEntry("status", "APPROVED")
			.containsEntry("reviewed_at", Timestamp.valueOf(NOW));
	}

	@Test
	@DisplayName("승인 불가는 고른 사유와 메모를 남긴다. 빈 메모는 남기지 않는다")
	void deniesWithReasonAndMemo() {
		long withMemo = fixture.submission(sellerId, 1, SubmissionStatus.NEEDS_REVIEW);
		long blankMemo = fixture.submission(sellerId, 1, SubmissionStatus.NEEDS_REVIEW);

		recordCertReviewUseCase.deny(sellerId, withMemo, DenyReason.NAME_MISMATCH, " 이름이 달라요 ", NOW);
		recordCertReviewUseCase.deny(sellerId, blankMemo, DenyReason.BLURRY, "  ", NOW);

		assertThat(row(withMemo))
			.containsEntry("status", "DENIED")
			.containsEntry("deny_reason", "NAME_MISMATCH")
			.containsEntry("deny_memo", "이름이 달라요");
		assertThat(row(blankMemo))
			.containsEntry("deny_reason", "BLURRY")
			.containsEntry("deny_memo", null);
	}

	@Test
	@DisplayName("남의 대회 제출은 심사할 수 없다 (403, 상태 그대로)")
	void othersSubmissionIsForbidden() {
		long otherSeller = fixture.approvedSeller();
		long submissionId = fixture.submission(otherSeller, 1, SubmissionStatus.NEEDS_REVIEW);

		assertThatThrownBy(() -> recordCertReviewUseCase.approve(sellerId, submissionId, NOW))
			.isInstanceOf(RecordCertNotOwnerException.class);
		assertThat(row(submissionId)).containsEntry("status", "NEEDS_REVIEW");
	}

	@Test
	@DisplayName("승인된 판매자가 아니면 자기 회원 ID가 적힌 제출이어도 심사할 수 없다 (403)")
	void notApprovedSellerIsForbidden() {
		long member = fixture.member();
		long submissionId = fixture.submission(member, 1, SubmissionStatus.NEEDS_REVIEW);

		assertThatThrownBy(() -> recordCertReviewUseCase.approve(member, submissionId, NOW))
			.isInstanceOf(RecordCertNotSellerException.class);
	}

	@Test
	@DisplayName("심사 필요가 아닌 제출은 심사할 수 없다 (409, 결과 그대로)")
	void onlyNeedsReviewIsReviewable() {
		for (SubmissionStatus status : SubmissionStatus.values()) {
			if (status == SubmissionStatus.NEEDS_REVIEW) {
				continue;
			}
			long submissionId = fixture.submission(sellerId, 1, status);

			assertThatThrownBy(() -> recordCertReviewUseCase.deny(sellerId, submissionId, DenyReason.OTHER, null,
				NOW)).isInstanceOf(SubmissionNotReviewableException.class);
			assertThat(row(submissionId)).containsEntry("status", status.name()).containsEntry("reviewed_at", null);
		}
	}

	@Test
	@DisplayName("심사를 읽은 뒤 환불 닫기가 먼저 저장했으면 심사는 409이고 환불 닫기 결과가 남는다")
	void concurrentRefundCloseWins() {
		long submissionId = fixture.submission(sellerId, 1, SubmissionStatus.NEEDS_REVIEW);
		TransactionTemplate refundClose = new TransactionTemplate(transactionManager);
		refundClose.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
			// 심사가 읽을 제출을 먼저 읽어 둔다(같은 트랜잭션이라 심사가 이 값을 다시 쓴다)
			entityManager.find(RecordCertSubmission.class, submissionId);
			refundClose.executeWithoutResult(other -> jdbcTemplate.update(
				"update member_record_cert_submission set status = 'CANCELED', version = version + 1"
					+ " where submission_id = ?", submissionId));

			assertThatThrownBy(() -> recordCertReviewUseCase.approve(sellerId, submissionId, NOW))
				.isInstanceOf(SubmissionNotReviewableException.class);
			tx.setRollbackOnly();
		});
		assertThat(row(submissionId)).containsEntry("status", "CANCELED").containsEntry("reviewed_at", null);
	}

	private Map<String, Object> row(long submissionId) {
		return jdbcTemplate.queryForMap("""
			select status, deny_reason, deny_memo, reviewed_at from member_record_cert_submission
			where submission_id = ?
			""", submissionId);
	}
}
