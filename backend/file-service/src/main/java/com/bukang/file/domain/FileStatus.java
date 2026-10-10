package com.bukang.file.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 파일 상태: 업로드 URL 발급 → 업로드 확인 → 대상(대회 등)에 연결 순서로 바뀐다
 */
@Getter
@RequiredArgsConstructor
public enum FileStatus {
	PENDING("업로드 대기"),
	UPLOADED("업로드 완료"), // 파일 업로드는 완료, refId가 채워지지 않은 상태
	ACTIVE("연결됨"), // 파일 업로드 및 refId가 채워진 상태
	DELETED("삭제됨");

	// 화면과 메시지에 쓰는 이름
	private final String label;
}
