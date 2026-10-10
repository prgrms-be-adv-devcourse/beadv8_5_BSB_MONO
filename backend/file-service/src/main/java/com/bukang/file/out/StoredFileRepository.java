package com.bukang.file.out;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.file.domain.FileStatus;
import com.bukang.file.domain.StoredFile;

public interface StoredFileRepository extends JpaRepository<StoredFile, Integer> {
	// 대상의 파일 (idx_file_ref 인덱스). 화면 순서는 StoredFile.DISPLAY_ORDER로 정렬한다
	// DB에서 file_type으로 정렬하면 알파벳순이 되어 대표 이미지가 맨 뒤로 가기 때문이다
	List<StoredFile> findAllByRefTypeAndRefIdAndStatus(String refType, Integer refId, FileStatus status);
}
