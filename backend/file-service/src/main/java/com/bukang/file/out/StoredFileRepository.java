package com.bukang.file.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.file.domain.FileStatus;
import com.bukang.file.domain.StoredFile;

public interface StoredFileRepository extends JpaRepository<StoredFile, Integer> {
	// 대상의 파일 (idx_file_ref 인덱스). 화면 순서는 StoredFile.DISPLAY_ORDER로 정렬한다
	// DB에서 file_type으로 정렬하면 알파벳순이 되어 대표 이미지가 맨 뒤로 가기 때문이다
	List<StoredFile> findAllByRefTypeAndRefIdAndStatus(String refType, Integer refId, FileStatus status);

	// 정리 후보: 대상 상태이고 기준 시각 전부터 바뀌지 않은 파일 (idx_file_status_modify 인덱스)
	// 지운 행은 다시 조회되지 않으므로 늘 첫 페이지(PageRequest.of(0, limit))를 읽으면 된다
	List<StoredFile> findByStatusInAndModifyDateBeforeOrderByIdAsc(Collection<FileStatus> statuses,
		LocalDateTime cutoff, Pageable pageable);

	// 배치가 정리 후보로 읽은 뒤 지우기 전까지 바뀌지 않았을 때만 지운다 (상태가 같고 기준 시각보다 오래됨)
	// 그 사이 업로드 완료(completeUpload)나 대상 연결(link)로 바뀌었으면 0을 돌려주고, 이때는 S3 객체도 지우지 않는다
	@Modifying
	@Query("delete from StoredFile f where f.id = :id and f.status = :status and f.modifyDate < :cutoff")
	int deleteIfOld(@Param("id") int id, @Param("status") FileStatus status, @Param("cutoff") LocalDateTime cutoff);
}
