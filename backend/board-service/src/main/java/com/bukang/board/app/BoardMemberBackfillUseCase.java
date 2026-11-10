package com.bukang.board.app;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bukang.board.out.member.MemberReplicaPort;
import com.bukang.board.out.member.MemberReplicaPort.MemberReplicaPage;

import lombok.extern.slf4j.Slf4j;

/**
 * 게시판이 뜰 때 회원 서비스에서 회원을 500명씩 받아, 복사본이 없거나 프로필이 비어 있는 회원만 채운다 (BUK-161)
 * Kafka 브로커가 오래된 메시지를 지워서 이벤트로는 받을 수 없는 옛 회원을 위한 것이다
 * 첫 호출이 실패하면 회원 서비스가 아직 뜨는 중일 수 있어 간격을 두고 3번 더 시도한다
 * 그래도 실패하거나 중간에 오류가 나면 로그만 남기고 멈춘다. 이미 채운 쪽은 남고, 다음 기동 때 다시 채운다
 * HTTP 호출은 트랜잭션 밖에서 하고, 한 쪽씩 BoardMemberSyncUseCase가 트랜잭션으로 넣는다
 */
@Slf4j
@Service
public class BoardMemberBackfillUseCase {
	static final int PAGE_SIZE = 500;
	static final int FIRST_CALL_RETRIES = 3;

	private final MemberReplicaPort memberReplicaPort;
	private final BoardMemberSyncUseCase boardMemberSyncUseCase;
	private final Duration retryInterval;

	public BoardMemberBackfillUseCase(MemberReplicaPort memberReplicaPort,
		BoardMemberSyncUseCase boardMemberSyncUseCase,
		@Value("${board.member-backfill.retry-interval:10s}") Duration retryInterval) {
		this.memberReplicaPort = memberReplicaPort;
		this.boardMemberSyncUseCase = boardMemberSyncUseCase;
		this.retryInterval = retryInterval;
	}

	public void run() {
		long afterId = 0;
		int received = 0;
		try {
			MemberReplicaPage page = fetchFirstPage();
			while (true) {
				boardMemberSyncUseCase.insertMissing(page.members());
				received += page.members().size();
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
				page = memberReplicaPort.fetch(afterId, PAGE_SIZE);
			}
			log.info("회원 복사본 채우기를 마쳤습니다. 받은 회원={}명", received);
		} catch (RuntimeException exception) {
			log.error("회원 복사본 채우기를 멈춥니다. 다음 기동 때 다시 채웁니다. afterId={}, 받은 회원={}명",
				afterId, received, exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			log.warn("회원 복사본 채우기가 중단됐습니다. 다음 기동 때 다시 채웁니다.");
		}
	}

	private MemberReplicaPage fetchFirstPage() throws InterruptedException {
		for (int retry = 0;; retry++) {
			try {
				return memberReplicaPort.fetch(0, PAGE_SIZE);
			} catch (RuntimeException exception) {
				if (retry == FIRST_CALL_RETRIES) {
					throw exception;
				}
				log.warn("회원 서비스에서 첫 쪽을 받지 못했습니다. {}초 뒤 다시 시도합니다. ({}/{})",
					retryInterval.toSeconds(), retry + 1, FIRST_CALL_RETRIES, exception);
				Thread.sleep(retryInterval);
			}
		}
	}
}
