package com.bukang.common.shared.file.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 파일 종류. 종류마다 한 파일의 최대 크기가 정해져 있다
 * 어떤 형식(MIME 타입, 매직 바이트)을 받는지는 file-service가 검사한다.
 */
@Getter
@RequiredArgsConstructor
public enum FileKind {
	IMAGE("이미지", 10L * 1024 * 1024), // 10MB
	VIDEO("동영상", 100L * 1024 * 1024), // 100MB
	EXCEL("엑셀", 5L * 1024 * 1024); // 5MB

	// 화면과 메시지에 쓰는 이름
	private final String label;
	// 한 파일의 최대 크기 (바이트)
	private final long maxSize;
}
