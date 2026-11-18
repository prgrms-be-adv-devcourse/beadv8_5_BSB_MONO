package com.bukang.member.recordcert.app;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.member.recordcert.domain.SubmissionStatus;
import com.bukang.member.recordcert.out.FileDeletePort;
import com.bukang.member.recordcert.out.RecordCertFileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 끝난 지 30일 지난 제출의 기록증 사진을 file-service에서 지운다 (BUK-278)
 * 끝난 날은 제출이 마지막으로 바뀐 시각(updated_at)이다. 같은 사진을 쓰는 제출이 진행 중이거나 끝난 지 30일이 안 됐으면 남긴다
 * 기록증의 값은 남기고 사진만 지운다. 지운 사진은 file_deleted_at을 적어 다음 날 다시 부르지 않는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordCertFileCleanupUseCase {
	private static final Duration RETENTION = Duration.ofDays(30);
	private static final List<SubmissionStatus> IN_PROGRESS = List.of(
		SubmissionStatus.CHECKING, SubmissionStatus.NEEDS_REVIEW);
	// shortcut: 하루에 이만큼만 지운다. 남은 사진은 다음 날 지운다. 제출이 하루 수천 건을 넘으면 묶음 반복으로 바꾼다
	private static final int DAILY_LIMIT = 500;

	private final RecordCertFileRepository recordCertFileRepository;
	private final FileDeletePort fileDeletePort;
	private final TransactionTemplate transactionTemplate;

	// 지운 사진 수를 돌려준다. 지우지 못한 사진은 표시하지 않아 다음 날 다시 부른다
	public int cleanup(LocalDateTime now) {
		List<Long> fileIds = recordCertFileRepository.findDeletableFileIds(
			IN_PROGRESS, now.minus(RETENTION), Limit.of(DAILY_LIMIT));
		int deleted = 0;
		for (long fileId : fileIds) {
			try {
				fileDeletePort.delete(fileId);
			} catch (RuntimeException exception) {
				log.warn("기록증 사진을 지우지 못했습니다. 다음 날 다시 지웁니다. fileId={}, cause={}", fileId, exception.toString());
				continue;
			}
			transactionTemplate.executeWithoutResult(status -> recordCertFileRepository.markFileDeleted(fileId, now));
			deleted++;
		}
		return deleted;
	}
}
