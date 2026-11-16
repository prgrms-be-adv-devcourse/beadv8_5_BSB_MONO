package com.bukang.product.app;

import java.util.List;

import com.bukang.common.shared.file.event.FileLinkRequestedEvent;
import com.bukang.common.shared.race.event.RaceChangedEvent;
import com.bukang.common.shared.race.event.RaceCourseChangedEvent;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.dto.RaceSaveDto;

/**
 * 대회 저장(등록·수정·취소) 유스케이스의 결과. ProductFacade가 같은 트랜잭션에서 파일 연결·대회 변경·종목 변경 메시지를 아웃박스에 남기는 데 쓴다.
 * 검색 문서는 RaceDocumentSyncScheduler가 DB를 다시 읽어 맞추므로 담지 않는다.
 */
public record RaceSaveResult(
	// API 응답
	RaceSaveDto dto,
	// 이미지 연결 메시지. 이미지를 바꾸지 않는 저장(취소)이면 null
	FileLinkRequestedEvent fileLink,
	// 대기열에 보낼 대회 변경 메시지. 대회 칸이 바뀌지 않은 수정이면 null
	RaceChangedEvent raceChanged,
	// 대기열에 보낼 종목 변경 메시지 (뺀 종목의 삭제 메시지 포함). 보낼 종목이 없으면 빈 목록
	List<RaceCourseChangedEvent> courseChanges,
	// AI 추천 벡터를 만들 글(Race.embeddingText)이 바뀌었는지 (BUK-162). true면 임베딩 동기화 이벤트를 남긴다
	// 등록은 늘 true, 수정은 수정 전후 글이 다를 때만 true, 취소·판매 중지·재개는 false
	boolean embeddingTextChanged
) {
	// 임베딩할 글이 바뀌지 않는 저장 (취소, 판매 중지·재개)
	public static RaceSaveResult of(Race race, FileLinkRequestedEvent fileLink,
		List<RaceCourseChangedEvent> courseChanges) {
		return of(race, fileLink, courseChanges, false);
	}

	public static RaceSaveResult of(Race race, FileLinkRequestedEvent fileLink,
		List<RaceCourseChangedEvent> courseChanges, boolean embeddingTextChanged) {
		List<RaceCourse> courses = race.getCourses();
		RaceSaveDto dto = new RaceSaveDto(
			race.getId(),
			race.getSaleStatus(),
			race.getSaleOpenDate(),
			courses.stream().map(RaceCourse::getId).toList()
		);
		return new RaceSaveResult(
			dto,
			fileLink,
			race.toChangedEvent(),
			courseChanges,
			embeddingTextChanged
		);
	}

	public long raceId() {
		return dto.getRaceId();
	}
}
