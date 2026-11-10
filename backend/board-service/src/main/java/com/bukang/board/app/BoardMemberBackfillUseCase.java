package com.bukang.board.app;

import org.springframework.stereotype.Service;

import com.bukang.board.out.member.MemberReplicaPort;
import com.bukang.board.out.member.MemberReplicaPort.MemberReplicaPage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 게시판이 뜰 때 회원 서비스에서 회원을 500명씩 받아, 복사본이 없는 회원만 채운다 (BUK-161)
 * Kafka 브로커가 오래된 메시지를 지워서 이벤트로는 받을 수 없는 옛 회원을 위한 것이다
 * 회원 서비스가 멈췄거나 오류가 나면 로그만 남기고 멈춘다. 이미 채운 쪽은 남고, 다음 기동 때 다시 채운다
 * HTTP 호출은 트랜잭션 밖에서 하고, 한 쪽씩 BoardMemberSyncUseCase가 트랜잭션으로 넣는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardMemberBackfillUseCase {
	static final int PAGE_SIZE = 500;

	private final MemberReplicaPort memberReplicaPort;
	private final BoardMemberSyncUseCase boardMemberSyncUseCase;

	public void run() {
		long afterId = 0;
		int inserted = 0;
		try {
			while (true) {
				MemberReplicaPage page = memberReplicaPort.fetch(afterId, PAGE_SIZE);
				inserted += boardMemberSyncUseCase.insertMissing(page.members());
				if (!page.hasNext()) {
					break;
				}
				if (page.nextAfterId() <= afterId) {
					// 받는 쪽이 같은 쪽을 계속 받게 되므로 계약이 어긋난 것으로 보고 멈춘다
					log.error("회원 복사본 채우기를 멈춥니다. 다음 afterId가 늘지 않았습니다. afterId={}, nextAfterId={}",
						afterId, page.nextAfterId());
					return;
				}
				afterId = page.nextAfterId();
			}
			log.info("회원 복사본 채우기를 마쳤습니다. 새로 넣은 회원={}명", inserted);
		} catch (RuntimeException exception) {
			log.error("회원 복사본 채우기를 멈춥니다. 다음 기동 때 다시 채웁니다. afterId={}, 새로 넣은 회원={}명",
				afterId, inserted, exception);
		}
	}
}
