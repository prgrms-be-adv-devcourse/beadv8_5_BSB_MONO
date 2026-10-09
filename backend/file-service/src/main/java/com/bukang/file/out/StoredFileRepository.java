package com.bukang.file.out;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.file.domain.FileStatus;
import com.bukang.file.domain.StoredFile;

public interface StoredFileRepository extends JpaRepository<StoredFile, Integer> {
	// 대상의 파일을 용도·노출 순서대로 (idx_file_ref 인덱스)
	List<StoredFile> findAllByRefTypeAndRefIdAndStatusOrderByImageTypeAscSortNoAscIdAsc(
		String refType,
		Integer refId,
		FileStatus status
	);
}
