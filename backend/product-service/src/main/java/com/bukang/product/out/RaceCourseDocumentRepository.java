package com.bukang.product.out;

import java.util.List;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.bukang.product.domain.document.RaceCourseDocument;

// 문서를 저장할 때는 save 대신 upsertAllExceptEmbedding·updateEmbedding을 쓴다 (save는 벡터 칸을 지운다)
public interface RaceCourseDocumentRepository extends ElasticsearchRepository<RaceCourseDocument, String>,
	RaceCourseDocumentPartialUpdateRepository {
	// 한 대회의 종목 문서를 모두 읽는다 (raceId는 keyword라 정확히 같은 값만 찾는다)
	// 색인할 때 DB에서 빠진 종목의 문서를 찾는 데 쓴다. 대회 하나의 종목 수는 적어 페이지로 나누지 않는다
	List<RaceCourseDocument> findByRaceId(String raceId);
}
