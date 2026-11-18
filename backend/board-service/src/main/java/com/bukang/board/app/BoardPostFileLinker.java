package com.bukang.board.app;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.bukang.board.domain.BoardPost;
import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.shared.file.domain.FileRef;
import com.bukang.common.shared.file.domain.FileType;
import com.bukang.common.shared.file.event.FileLinkRequestedEvent;

import lombok.RequiredArgsConstructor;

/**
 * 글의 사진 연결 요청을 이벤트로 발행한다. 부른 쪽의 트랜잭션 안에서 부르고, BoardFileLinkSender가 커밋된 뒤에만 Kafka로 보낸다
 * 보낸 목록으로 사진을 통째로 바꾸므로 빈 목록이면 사진을 모두 뗀다
 */
@Component
@RequiredArgsConstructor
public class BoardPostFileLinker {
	private final EventPublisher eventPublisher;

	public void link(long memberId, BoardPost post, List<Long> fileIds) {
		List<FileLinkRequestedEvent.Item> items = IntStream.range(0, fileIds.size())
			.mapToObj(index -> new FileLinkRequestedEvent.Item(fileIds.get(index), FileType.POST_IMAGE, index))
			.toList();
		eventPublisher.publish(new FileLinkRequestedEvent(memberId,
			List.of(FileLinkRequestedEvent.Ref.of(FileRef.of(post), items))));
	}

	// 글을 지울 때 사진 연결을 모두 뗀다. 관리자가 지울 때도 쓴 사람 이름으로 보낸다
	public void releaseAll(long memberId, BoardPost post) {
		link(memberId, post, List.of());
	}
}
