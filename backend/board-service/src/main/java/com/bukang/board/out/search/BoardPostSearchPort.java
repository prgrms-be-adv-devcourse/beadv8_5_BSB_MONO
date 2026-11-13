package com.bukang.board.out.search;

import java.util.List;

/**
 * 글 검색 인덱스(Elasticsearch)로 가는 길. 인덱스에는 제목·본문만 넣고 글 ID로 찾는다.
 * 보이는지(숨김·삭제)와 말머리는 인덱스에 넣지 않고 DB에서 거른다. 인덱스는 "어떤 글이 검색어에 맞는가"만 안다
 */
public interface BoardPostSearchPort {
	record Document(long postId, String title, String content) {
	}

	// 검색어에 맞는 글 ID를 최신(ID가 큰) 글부터 limit개 돌려준다. 인덱스가 아직 없으면 빈 목록이다
	// 연결하지 못하면 BoardExternalServiceUnavailableException(503)
	List<Long> search(String query, int limit);

	// 글 하나를 넣는다. 같은 글을 다시 넣으면 내용만 바뀐다. 실패하면 예외를 던진다 (호출한 쪽이 다시 시도한다)
	void index(Document document);

	// 여러 글을 한 번에 넣는다 (기존 글 채우기)
	void indexAll(List<Document> documents);

	// 글 하나를 지운다. 인덱스에 없어도 오류가 아니다
	void delete(long postId);
}
