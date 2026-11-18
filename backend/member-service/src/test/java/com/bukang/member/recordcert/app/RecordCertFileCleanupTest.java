package com.bukang.member.recordcert.app;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.recordcert.domain.SubmissionStatus;
import com.bukang.member.recordcert.out.FileDeletePort;

/**
 * 끝난 지 30일 지난 제출의 기록증 사진 삭제 (BUK-278)
 * - 끝난 제출(자동 승인 · 승인 · 반려 · 승인 불가 · 취소됨)은 끝난 날(마지막으로 바뀐 시각)부터 30일 뒤 지운다
 * - 같은 사진을 쓰는 다른 제출이 진행 중(확인 중 · 심사 필요)이거나 끝난 지 30일이 안 됐으면 남긴다
 * - 지운 사진은 다음 날 다시 지우라고 부르지 않는다
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class RecordCertFileCleanupTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 20, 4, 40);
	private static final LocalDateTime OVER_30_DAYS = NOW.minusDays(30).minusSeconds(1);
	private static final LocalDateTime UNDER_30_DAYS = NOW.minusDays(30).plusSeconds(1);

	@Autowired
	private RecordCertFileCleanupUseCase recordCertFileCleanupUseCase;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@MockitoBean
	private FileDeletePort fileDeletePort;

	@AfterEach
	void tearDown() {
		jdbcTemplate.update("delete from member_record_cert_submission");
		jdbcTemplate.update("delete from member_record_cert");
	}

	@Test
	@DisplayName("끝난 지 30일 지난 제출의 사진은 상태와 상관없이 지운다")
	void deletesFilesOfSubmissionsEndedOver30DaysAgo() {
		long fileId = 100;
		for (SubmissionStatus ended : new SubmissionStatus[] {SubmissionStatus.AUTO_APPROVED,
			SubmissionStatus.APPROVED, SubmissionStatus.REJECTED, SubmissionStatus.DENIED,
			SubmissionStatus.CANCELED}) {
			submission(cert(++fileId), ended, OVER_30_DAYS);
		}

		recordCertFileCleanupUseCase.cleanup(NOW);

		for (long id = 101; id <= 105; id++) {
			verify(fileDeletePort).delete(id);
		}
	}

	@Test
	@DisplayName("끝난 지 30일이 안 됐거나 진행 중인 제출의 사진은 남긴다")
	void keepsRecentOrInProgress() {
		submission(cert(201), SubmissionStatus.APPROVED, UNDER_30_DAYS);
		submission(cert(202), SubmissionStatus.CHECKING, OVER_30_DAYS);
		submission(cert(203), SubmissionStatus.NEEDS_REVIEW, OVER_30_DAYS);

		recordCertFileCleanupUseCase.cleanup(NOW);

		verify(fileDeletePort, never()).delete(anyLong());
	}

	@Test
	@DisplayName("같은 사진을 다시 낸 제출이 진행 중이거나 끝난 지 30일이 안 됐으면 남긴다")
	void keepsSharedPhotoInUse() {
		long resubmittedSameCert = cert(301);
		submission(resubmittedSameCert, SubmissionStatus.REJECTED, OVER_30_DAYS);
		submission(resubmittedSameCert, SubmissionStatus.NEEDS_REVIEW, UNDER_30_DAYS);
		submission(cert(302), SubmissionStatus.REJECTED, OVER_30_DAYS);
		submission(cert(302), SubmissionStatus.APPROVED, UNDER_30_DAYS);

		recordCertFileCleanupUseCase.cleanup(NOW);

		verify(fileDeletePort, never()).delete(anyLong());
	}

	@Test
	@DisplayName("지운 사진은 다음 날 다시 지우라고 부르지 않고, 지우지 못한 사진은 다음 날 다시 부른다")
	void doesNotDeleteTwice() {
		submission(cert(401), SubmissionStatus.APPROVED, OVER_30_DAYS);
		submission(cert(402), SubmissionStatus.APPROVED, OVER_30_DAYS);
		doThrow(new IllegalStateException("file 서비스 응답 없음")).when(fileDeletePort).delete(402);

		recordCertFileCleanupUseCase.cleanup(NOW);
		recordCertFileCleanupUseCase.cleanup(NOW.plusDays(1));

		verify(fileDeletePort, times(1)).delete(401);
		verify(fileDeletePort, times(2)).delete(402);
	}

	private long cert(long fileId) {
		jdbcTemplate.update("""
			insert into member_record_cert (member_id, file_id, roi_x, roi_y, roi_width, roi_height,
				net_time_seconds, race_date, course_class, created_at)
			values (1, ?, 0, 0, 10, 10, 3600, '2026-11-01', 'TEN_K', ?)
			""", fileId, NOW.minusDays(60));
		return jdbcTemplate.queryForObject("select max(record_cert_id) from member_record_cert", Long.class);
	}

	private void submission(long recordCertId, SubmissionStatus status, LocalDateTime endedAt) {
		jdbcTemplate.update("""
			insert into member_record_cert_submission (record_cert_id, member_id, order_item_id, race_id,
				race_course_id, status, created_at, updated_at)
			values (?, 1, 1, 1, 1, ?, ?, ?)
			""", recordCertId, status.name(), NOW.minusDays(60), endedAt);
	}
}
