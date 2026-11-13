package com.bukang.board.domain;

/**
 * 글이 바뀌었다는 메시지 (토픽 queuing.post.changed). 글 ID만 담는다.
 * 받는 쪽이 처리하는 순간의 DB 글 상태로 인덱스를 맞추므로, 내용을 담으면 오래된 내용이 최신을 덮을 수 있다
 */
public record BoardPostChangedEvent(long postId) {
}
