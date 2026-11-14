package com.bukang.product.out;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;

import com.bukang.product.domain.document.RaceCourseDocument;

import lombok.RequiredArgsConstructor;

/**
 * RaceCourseDocumentPartialUpdateRepository 구현. 이름 끝의 Impl로 Spring Data가 RaceCourseDocumentRepository에 붙여 준다
 */
@RequiredArgsConstructor
public class RaceCourseDocumentPartialUpdateRepositoryImpl implements RaceCourseDocumentPartialUpdateRepository {
	// BaseDocument의 마지막 색인 시각 칸과 날짜 형식 (date_hour_minute_second_millis)
	private static final String LAST_MODIFIED_AT = "lastModifiedAt";
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

	private final ElasticsearchOperations elasticsearchOperations;

	@Override
	public void upsertAllExceptEmbedding(List<RaceCourseDocument> documents, LocalDateTime now) {
		if (documents.isEmpty()) {
			return;
		}
		List<UpdateQuery> queries = documents.stream()
			.map(document -> UpdateQuery.builder(document.getId())
				.withDocument(sourceExceptEmbedding(document, now))
				.withDocAsUpsert(true)
				.build())
			.toList();
		elasticsearchOperations.bulkUpdate(queries, RaceCourseDocument.class);
		// 저장소의 saveAll처럼 바로 검색에 보이게 새로고침한다 (하지 않으면 ES가 1초 안에 알아서 반영한다)
		// 동기화가 이어서 이 대회의 문서를 다시 찾아(findByRaceId) 지운 종목의 문서를 지우므로, 방금 고친 문서가 보여야 한다
		elasticsearchOperations.indexOps(RaceCourseDocument.class).refresh();
	}

	@Override
	public void updateEmbedding(List<String> documentIds, float[] embedding) {
		if (documentIds.isEmpty()) {
			return;
		}
		List<UpdateQuery> queries = documentIds.stream()
			.map(documentId -> UpdateQuery.builder(documentId)
				.withDocument(embeddingOnly(embedding))
				.build())
			.toList();
		// 문서가 없는 항목이 있으면 bulkUpdate가 BulkFailureException을 낸다 (부른 쪽이 실패로 기록해 다시 시도한다)
		elasticsearchOperations.bulkUpdate(queries, RaceCourseDocument.class);
		// 저장한 벡터가 바로 KNN 검색에 보이게 새로고침한다
		elasticsearchOperations.indexOps(RaceCourseDocument.class).refresh();
	}

	// 벡터 칸 하나만 담은 부분 수정 본문
	private static Document embeddingOnly(float[] embedding) {
		Document source = Document.create();
		source.put(RaceCourseDocument.RACE_EMBEDDING, embedding);
		return source;
	}

	// 문서를 ES에 저장할 모양(_source)으로 바꾼 뒤 벡터 칸을 뺀다
	// 벡터가 null이어도 칸 이름을 남기면 기존 벡터를 null로 덮을 수 있어 항상 뺀다
	private Document sourceExceptEmbedding(RaceCourseDocument document, LocalDateTime now) {
		Document source = elasticsearchOperations.getElasticsearchConverter().mapObject(document);
		source.remove(RaceCourseDocument.RACE_EMBEDDING);
		source.put(LAST_MODIFIED_AT, now.format(DATE_TIME));
		return source;
	}
}
