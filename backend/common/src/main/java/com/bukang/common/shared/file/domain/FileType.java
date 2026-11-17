package com.bukang.common.shared.file.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 파일 용도. 파일을 연결하는 서비스(대회 등)와 file-service가 같은 값을 쓰도록 common에 둔다
 * 용도마다 올릴 수 있는 파일 종류와, 대상 하나에 연결할 수 있는 최대 개수가 정해져 있다.
 * 이미지는 용도에 따라 최소 가로 픽셀도 정해져 있다.
 */
@Getter
@RequiredArgsConstructor
public enum FileType {
	// 대회마다 1장. 종목이 아니라 대회에 붙인다
	THUMBNAIL("대표 이미지", FileKind.IMAGE, 1, 0),
	// 상세 본문에 세로로 이어 붙이므로 가로 1080px 이상만 받는다 (세로 제한 없음)
	DETAIL("대회 소개 이미지", FileKind.IMAGE, 5, 1080),
	// 종목(풀·하프·30km대·10km·5km·5km 미만 건강달리기·울트라)마다 1장
	COURSE("코스 안내 이미지", FileKind.IMAGE, 7, 0),
	// 종목에 포함된 기념품(키트) 이미지. 종목마다 1장
	KIT("키트 이미지", FileKind.IMAGE, 1, 0),
	INTRO_VIDEO("대회 소개 영상", FileKind.VIDEO, 1, 0),
	ROSTER("크루원 명단", FileKind.EXCEL, 1, 0),
	// 게시판 글에 붙이는 사진. 글마다 최대 5장
	POST_IMAGE("게시글 사진", FileKind.IMAGE, 5, 0);

	// 화면과 메시지에 쓰는 이름
	private final String label;
	private final FileKind kind;
	// 대상 하나에 연결할 수 있는 최대 개수
	private final int maxCount;
	// 이미지의 최소 가로 픽셀. 0이면 검사하지 않는다
	private final int minWidth;
}
