package com.bukang.common.shared.file.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 파일 용도. 파일을 연결하는 서비스(대회 등)와 file-service가 같은 값을 쓰도록 common에 둔다
 * 용도마다 올릴 수 있는 파일 종류와, 대상 하나에 연결할 수 있는 최대 개수가 정해져 있다.
 */
@Getter
@RequiredArgsConstructor
public enum FileType {
	THUMBNAIL("대표 이미지", FileKind.IMAGE, 1),
	DETAIL("대회 소개 이미지", FileKind.IMAGE, 5),
	// 종목(풀·하프·30km대·10km·5km·5km 미만 건강달리기·울트라)마다 1장
	COURSE("코스 안내 이미지", FileKind.IMAGE, 7),
	INTRO_VIDEO("대회 소개 영상", FileKind.VIDEO, 1),
	ROSTER("크루원 명단", FileKind.EXCEL, 1);

	// 화면과 메시지에 쓰는 이름
	private final String label;
	private final FileKind kind;
	// 대상 하나에 연결할 수 있는 최대 개수
	private final int maxCount;
}
