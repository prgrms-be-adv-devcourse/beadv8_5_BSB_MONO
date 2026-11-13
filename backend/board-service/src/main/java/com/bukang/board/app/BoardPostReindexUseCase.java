package com.bukang.board.app;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.bukang.board.domain.BoardPost;
import com.bukang.board.domain.BoardPostStatus;
import com.bukang.board.out.BoardPostRepository;
import com.bukang.board.out.search.BoardPostSearchPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 보이는 글을 500개씩 검색 인덱스에 넣는다 (BUK-179). 검색을 붙이기 전에 쓴 글과, 메시지가 끝내 안 간 글을 채운다
 * 같은 글을 다시 넣으면 내용만 바뀌므로 몇 번 돌려도 같은 결과다. 숨기거나 지운 글은 넣지 않는다
 * Elasticsearch가 멈춰 있거나 중간에 오류가 나면 로그만 남기고 멈춘다. 다음 기동 때 다시 채운다
 * 채우는 동안 글이 숨겨져도, 검색은 DB에서 보이는 글만 거르므로 목록에 나오지 않는다 (인덱스에 옛 문서가 남을 뿐이다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardPostReindexUseCase {
	static final int BATCH_SIZE = 500;

	private final BoardPostRepository boardPostRepository;
	private final BoardPostSearchPort boardPostSearchPort;

	// 인덱스에 넣은 글 수를 돌려준다
	public int run() {
		long afterId = 0;
		int indexed = 0;
		try {
			while (true) {
				List<BoardPost> posts = boardPostRepository
					.findByStatusAndIdGreaterThanOrderByIdAsc(BoardPostStatus.VISIBLE, afterId, Limit.of(BATCH_SIZE));
				if (posts.isEmpty()) {
					break;
				}
				boardPostSearchPort.indexAll(posts.stream()
					.map(post -> new BoardPostSearchPort.Document(post.getId(), post.getTitle(), post.getContent()))
					.toList());
				indexed += posts.size();
				afterId = posts.getLast().getId();
				if (posts.size() < BATCH_SIZE) {
					break;
				}
			}
			log.info("글 검색 인덱스 채우기를 마쳤습니다. 넣은 글={}개", indexed);
		} catch (RuntimeException exception) {
			log.error("글 검색 인덱스 채우기를 멈춥니다. 다음 기동 때 다시 채웁니다. afterId={}, 넣은 글={}개",
				afterId, indexed, exception);
		}
		return indexed;
	}
}
