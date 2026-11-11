package com.bukang.product.out;

import java.time.LocalDateTime;
import java.util.List;

import com.bukang.product.domain.document.RaceCourseDocument;

/**
 * 검색 문서를 칸 일부만 고친다 (RaceCourseDocumentRepository에 붙는 조각, BUK-162)
 * 문서 동기화와 임베딩 동기화가 서로의 칸을 건드리지 않게 나눠 고친다.
 * - 문서 동기화: 벡터 칸(raceEmbedding)을 뺀 나머지 칸 (upsertAllExceptEmbedding)
 * - 임베딩 동기화: 벡터 칸만 (updateEmbedding)
 * save로 통째로 덮어쓰면 문서에 없는 벡터 칸이 지워진다 (ES 9.x는 조회 결과에 벡터를 주지 않아 읽어서 다시 넣을 수도 없다)
 */
public interface RaceCourseDocumentPartialUpdateRepository {
	// 문서가 있으면 벡터 칸만 남기고 나머지 칸을 고치고, 없으면 새로 만든다 (doc_as_upsert)
	// now는 마지막 색인 시각(lastModifiedAt)으로 넣는다. 부분 수정은 Auditing을 거치지 않기 때문이다
	void upsertAllExceptEmbedding(List<RaceCourseDocument> documents, LocalDateTime now);

	// 문서들의 벡터 칸만 고친다. 문서를 새로 만들지 않는다 (upsert가 아니다)
	// 문서가 하나라도 없으면 예외를 낸다(ES 문서 없음). 문서 동기화가 아직 문서를 만들기 전이므로 부른 쪽이 다시 시도한다
	//   upsert로 하면 벡터만 있는 반쪽 문서가 생기고, 그사이 종목이 지워지면 raceId가 없어 아무도 지우지 못한다
	void updateEmbedding(List<String> documentIds, float[] embedding);
}
