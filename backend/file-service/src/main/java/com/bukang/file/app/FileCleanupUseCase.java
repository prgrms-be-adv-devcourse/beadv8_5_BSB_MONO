package com.bukang.file.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.bukang.file.domain.FileStatus;
import com.bukang.file.domain.StoredFile;
import com.bukang.file.out.StoredFileRepository;
import com.bukang.file.out.storage.FileStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 정리 배치(PRO-57): 마지막으로 바뀐 뒤 기준 시각이 지난 미연결·삭제 파일의 행과 S3 객체를 지운다.
 * 행 삭제는 FileFacade가 연 트랜잭션 안에서 부르고, S3 삭제는 그 트랜잭션이 커밋된 뒤에 부른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCleanupUseCase {
	// 연결된 파일(ACTIVE)은 지우지 않는다
	private static final List<FileStatus> CLEANUP_TARGETS = List.of(
		FileStatus.PENDING, FileStatus.UPLOADED, FileStatus.DELETED);

	private final StoredFileRepository storedFileRepository;
	private final FileStorage fileStorage;

	// 후보를 limit건 읽어 한 건씩 조건부로 지우고, 실제로 지운 행의 S3 키를 돌려준다
	// 지운 행은 다시 조회되지 않으므로 늘 첫 페이지를 읽는다 (학원 방식)
	public List<String> deleteOldRows(int limit, LocalDateTime cutoff) {
		List<StoredFile> candidates = storedFileRepository.findByStatusInAndModifyDateBeforeOrderByIdAsc(
			CLEANUP_TARGETS, cutoff, PageRequest.of(0, limit));

		List<String> deletedKeys = new ArrayList<>();
		for (StoredFile file : candidates) {
			// 0이면 후보로 읽은 뒤 업로드 완료·대상 연결로 바뀐 파일이므로 S3 객체도 지우지 않는다
			if (storedFileRepository.deleteIfOld(file.getId(), file.getStatus(), cutoff) == 1) {
				deletedKeys.add(file.getS3Key());
			}
		}
		return deletedKeys;
	}

	// 행 삭제가 커밋된 뒤에 부른다. 지우지 못한 객체는 행 없이 S3에만 남으므로 로그로 찾는다
	public void deleteObjects(List<String> keys) {
		if (keys.isEmpty()) {
			return;
		}
		try {
			List<String> failedKeys = fileStorage.deleteAll(keys);
			log.info("파일 {}건을 정리했습니다 (S3 객체 삭제 실패 {}건)", keys.size(), failedKeys.size());
		} catch (RuntimeException exception) {
			// 요청 자체가 실패하면(네트워크, 권한 등) 다음 묶음도 실패할 가능성이 높다
			// 계속 돌면 행만 지워지고 객체가 쌓이므로, 남은 키를 남기고 예외를 다시 던져 배치를 멈춘다
			log.error("S3 객체 삭제 요청이 실패했습니다. 행만 지워진 파일 {}건: {}", keys.size(), keys);
			throw exception;
		}
	}
}
