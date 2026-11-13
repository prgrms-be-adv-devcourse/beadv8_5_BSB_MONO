package com.bukang.board.out.search;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.bukang.board.domain.exception.BoardExternalServiceUnavailableException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 게시판 전용 인덱스(board_posts)로 글을 넣고 찾는다 (BUK-179). 대회 검색 인덱스(race_courses)는 건드리지 않는다.
 * 분석기는 board-post-index.json: 색인은 원래 단어와 쪼갠 조각을 함께, 검색은 쪼갠 조각만 쓴다 (nori, 조사·어미 제거).
 * 인덱스는 처음 넣을 때 없으면 만든다. 검색어의 모든 단어가 제목 한 곳이나 본문 한 곳 안에 있어야 맞는다 (AND). 단어가 제목과 본문에 나뉘어 있으면 맞지 않는다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticBoardPostSearchAdapter implements BoardPostSearchPort {
	static final String INDEX = "board_posts";
	private static final String SETTINGS_FILE = "/elasticsearch/board-post-index.json";

	private final ElasticsearchClient client;

	@Override
	public List<Long> search(String query, int limit) {
		try {
			// 인덱스가 아직 없으면(글이 하나도 색인되기 전) 오류 대신 빈 결과를 받는다
			SearchResponse<Void> response = client.search(search -> search
				.index(INDEX)
				.ignoreUnavailable(true)
				.size(limit)
				.source(source -> source.fetch(false))
				.query(q -> q.multiMatch(match -> match.query(query).fields("title", "content")
					.operator(Operator.And)))
				.sort(sort -> sort.field(field -> field.field("postId").order(SortOrder.Desc))), Void.class);
			return response.hits().hits().stream().map(Hit::id).map(Long::valueOf).toList();
		} catch (IOException | RuntimeException exception) {
			log.warn("글 검색 인덱스를 읽지 못했습니다. cause={}", exception.toString());
			throw new BoardExternalServiceUnavailableException("검색을 잠시 쓸 수 없습니다. 잠시 뒤 다시 시도해주세요.");
		}
	}

	@Override
	public void index(Document document) {
		try {
			ensureIndex();
			client.index(index -> index.index(INDEX).id(String.valueOf(document.postId()))
				.document(body(document)));
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	@Override
	public void indexAll(List<Document> documents) {
		if (documents.isEmpty()) {
			return;
		}
		try {
			ensureIndex();
			BulkResponse response = client.bulk(bulk -> {
				documents.forEach(document -> bulk.operations(operation -> operation.index(index -> index
					.index(INDEX).id(String.valueOf(document.postId())).document(body(document)))));
				return bulk;
			});
			if (response.errors()) {
				String reason = response.items().stream().filter(item -> item.error() != null).findFirst()
					.map(item -> item.error().reason()).orElse("알 수 없음");
				throw new IllegalStateException("글 일부를 색인하지 못했습니다. " + reason);
			}
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	@Override
	public void delete(long postId) {
		try {
			client.delete(delete -> delete.index(INDEX).id(String.valueOf(postId)));
		} catch (ElasticsearchException exception) {
			// 인덱스나 문서가 없으면 이미 지워진 것과 같다
			if (exception.status() != 404) {
				throw exception;
			}
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static Map<String, Object> body(Document document) {
		return Map.of("postId", document.postId(), "title", document.title(), "content", document.content());
	}

	// 인덱스가 없으면 nori 분석기와 함께 만든다. 두 곳에서 동시에 만들어 "이미 있음"이 나와도 정상이다
	private void ensureIndex() throws IOException {
		if (client.indices().exists(exists -> exists.index(INDEX)).value()) {
			return;
		}
		try (InputStream settings = getClass().getResourceAsStream(SETTINGS_FILE)) {
			client.indices().create(create -> create.index(INDEX).withJson(settings));
		} catch (ElasticsearchException exception) {
			if (exception.response().error() == null
				|| !"resource_already_exists_exception".equals(exception.response().error().type())) {
				throw exception;
			}
		}
	}
}
