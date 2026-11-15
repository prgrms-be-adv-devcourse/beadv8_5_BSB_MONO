package com.bukang.board.app;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.domain.BoardPost;
import com.bukang.board.domain.exception.BoardNotWriterException;
import com.bukang.board.domain.exception.BoardPostHiddenException;
import com.bukang.board.domain.exception.BoardTooFastException;
import com.bukang.board.dto.PostSaveRequestDto;
import com.bukang.board.out.BoardCooldown;
import com.bukang.board.out.BoardPostRepository;
import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.shared.file.domain.FileRef;
import com.bukang.common.shared.file.domain.FileType;
import com.bukang.common.shared.file.event.FileLinkRequestedEvent;

import lombok.RequiredArgsConstructor;

/**
 * 글 쓰기·고치기·지우기. 사진 연결 요청은 트랜잭션 안에서 이벤트로 발행하고,
 * BoardFileLinkSender가 커밋된 뒤에만 Kafka로 보낸다 (롤백되면 보내지 않는다)
 */
@Service
@RequiredArgsConstructor
public class BoardPostUseCase {
	private final BoardPostRepository boardPostRepository;
	private final BoardPostFinder boardPostFinder;
	private final BoardCooldown boardCooldown;
	private final EventPublisher eventPublisher;
	private final BoardWriterGuard boardWriterGuard;

	// 입력 검사는 컨트롤러(@Valid)가 끝냈다. 도배 제한은 검사를 통과한 요청에만 센다
	@Transactional
	public long create(long memberId, PostSaveRequestDto request) {
		boardWriterGuard.checkNotWithdrawn(memberId);
		if (!boardCooldown.acquirePost(memberId)) {
			throw new BoardTooFastException();
		}
		BoardPost post = boardPostRepository.save(
			BoardPost.create(memberId, request.categoryOrDefault(), request.title(), request.content()));
		if (request.fileIds() != null && !request.fileIds().isEmpty()) {
			requestFileLink(memberId, post, request.fileIds());
		}
		return post.getId();
	}

	// 남의 글은 403, 숨겨진 내 글은 409. fileIds를 빼면 사진을 건드리지 않고, 빈 목록이면 사진을 모두 뗀다
	@Transactional
	public void update(long memberId, long postId, PostSaveRequestDto request) {
		boardWriterGuard.checkNotWithdrawn(memberId);
		BoardPost post = boardPostFinder.findViewable(postId, memberId);
		if (!post.isWrittenBy(memberId)) {
			throw new BoardNotWriterException();
		}
		if (post.isHidden()) {
			throw new BoardPostHiddenException();
		}
		post.edit(request.categoryOrDefault(), request.title(), request.content());
		if (request.fileIds() != null) {
			requestFileLink(memberId, post, request.fileIds());
		}
	}

	// 숨겨진 내 글도 지울 수 있다 (스스로 내리는 것이라 막을 이유가 없다)
	@Transactional
	public void delete(long memberId, long postId) {
		boardWriterGuard.checkNotWithdrawn(memberId);
		BoardPost post = boardPostFinder.findViewable(postId, memberId);
		if (!post.isWrittenBy(memberId)) {
			throw new BoardNotWriterException();
		}
		post.delete();
		// 빈 목록을 보내야 file-service가 사진 연결을 떼어, 지운 글의 사진이 공개 조회에 남지 않고 정리 배치 대상이 된다
		requestFileLink(memberId, post, List.of());
	}

	// 관리자가 글을 지울 때도 사진 연결을 뗀다 (쓴 사람 이름으로 보낸다)
	public void releaseFiles(BoardPost post) {
		requestFileLink(post.getMemberId(), post, List.of());
	}

	private void requestFileLink(long memberId, BoardPost post, List<Long> fileIds) {
		List<FileLinkRequestedEvent.Item> items = IntStream.range(0, fileIds.size())
			.mapToObj(index -> new FileLinkRequestedEvent.Item(fileIds.get(index), FileType.POST_IMAGE, index))
			.toList();
		eventPublisher.publish(new FileLinkRequestedEvent(memberId,
			List.of(FileLinkRequestedEvent.Ref.of(FileRef.of(post), items))));
	}
}
