package com.bukang.board.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClientException;

import com.bukang.board.domain.BoardMember;
import com.bukang.board.out.member.MemberReplicaPort;
import com.bukang.board.out.member.MemberReplicaPort.MemberReplica;
import com.bukang.board.out.member.MemberReplicaPort.MemberReplicaPage;
import com.bukang.board.support.BoardTestBase;

/**
 * 게시판이 뜰 때 회원 서비스에서 받아 복사본을 채우는 일: 없는 회원만 넣고, 끝까지 받고, 실패해도 조용히 멈춘다
 */
class BoardMemberBackfillUseCaseTest extends BoardTestBase {
	private static final LocalDateTime T1 = LocalDateTime.of(2026, 11, 1, 9, 0);
	private static final LocalDateTime T2 = LocalDateTime.of(2026, 11, 5, 9, 0);

	@Autowired
	private BoardMemberBackfillUseCase backfill;
	@Autowired
	private BoardMemberSyncUseCase sync;
	@MockitoBean
	private MemberReplicaPort port;

	@Test
	@DisplayName("복사본이 없는 회원을 닉네임·판매자 여부·탈퇴 여부·수정 시각 그대로 채운다")
	void fillsMissingMembers() {
		when(port.fetch(0, 500)).thenReturn(page(false, 12,
			replica(10, "러너셋", true, false, T1), replica(12, "탈퇴한 회원", false, true, T2)));

		backfill.run();

		BoardMember seller = memberRepository.findById(10L).orElseThrow();
		assertThat(seller.getNickname()).isEqualTo("러너셋");
		assertThat(seller.isSellerApproved()).isTrue();
		assertThat(seller.isWithdrawn()).isFalse();
		assertThat(seller.getModifyDate()).isEqualTo(T1);
		BoardMember withdrawn = memberRepository.findById(12L).orElseThrow();
		assertThat(withdrawn.isWithdrawn()).isTrue();
		assertThat(withdrawn.getNickname()).isEqualTo("탈퇴한 회원");
		assertThat(withdrawn.getModifyDate()).isEqualTo(T2);
	}

	@Test
	@DisplayName("이미 복사본이 있는 회원은 건드리지 않는다 (이벤트로 받은 최신 값을 옛 값이 덮지 않음)")
	void keepsExistingMembers() {
		when(port.fetch(0, 500)).thenReturn(page(false, 11,
			replica(ME, "옛닉네임", true, true, T1), replica(11, "새회원", false, false, T1)));

		backfill.run();

		BoardMember me = memberRepository.findById(ME).orElseThrow();
		assertThat(me.getNickname()).isEqualTo("러너하나");
		assertThat(me.isSellerApproved()).isFalse();
		assertThat(me.isWithdrawn()).isFalse();
		assertThat(memberRepository.findById(11L)).isPresent();
	}

	@Test
	@DisplayName("hasNext가 true면 nextAfterId로 이어서 마지막 쪽까지 500명씩 받는다")
	void readsAllPages() {
		when(port.fetch(0, 500)).thenReturn(page(true, 20, replica(20, "일", false, false, T1)));
		when(port.fetch(20, 500)).thenReturn(page(true, 30, replica(30, "이", false, false, T1)));
		when(port.fetch(30, 500)).thenReturn(page(false, 40, replica(40, "삼", false, false, T1)));

		backfill.run();

		verify(port, times(3)).fetch(anyLong(), anyInt());
		assertThat(memberRepository.findAllById(List.of(20L, 30L, 40L))).hasSize(3);
	}

	@Test
	@DisplayName("채운 뒤 더 옛 수정 시각의 가입 메시지가 늦게 와도 닉네임을 되돌리지 않고, 판매자 메시지는 그대로 반영한다")
	void laterEventsStillApplyCorrectly() {
		when(port.fetch(0, 500)).thenReturn(page(false, 10, replica(10, "최신닉", false, false, T2)));
		backfill.run();

		sync.syncProfile("m-old", "topic", 10, T1, T1, "옛닉");
		sync.syncSeller("m-seller", "topic", 10, true, T2.plusDays(1));

		BoardMember member = memberRepository.findById(10L).orElseThrow();
		assertThat(member.getNickname()).isEqualTo("최신닉");
		assertThat(member.isSellerApproved()).isTrue();
	}

	@Test
	@DisplayName("채우기와 이벤트가 같은 회원을 동시에 넣어도 채우기는 오류 없이 끝나고 행은 하나만 들어간다")
	void concurrentInsertKeepsOneRow() throws Exception {
		when(port.fetch(0, 500)).thenReturn(page(false, 10, replica(10, "동시", false, false, T1)));
		ExecutorService executor = Executors.newFixedThreadPool(4);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<?>> backfills = List.of(
				executor.submit(() -> awaitThen(start, backfill::run)),
				executor.submit(() -> awaitThen(start, backfill::run)));
			// 이벤트는 기존 방식(읽고 저장)이라 진 쪽이 키 중복으로 실패할 수 있다. 실패하면 롤백돼 Kafka가 다시 보내므로 여기서는 허용한다
			List<Future<?>> events = List.of(
				executor.submit(() -> awaitThen(start, () -> sync.syncProfile("m-1", "topic", 10, T1, T1, "이벤트"))),
				executor.submit(() -> awaitThen(start, () -> sync.syncProfile("m-2", "topic", 10, T1, T1, "이벤트"))));
			start.countDown();
			for (Future<?> future : backfills) {
				future.get();
			}
			for (Future<?> future : events) {
				try {
					future.get();
				} catch (ExecutionException exception) {
					assertThat(exception.getCause()).isInstanceOf(DataIntegrityViolationException.class);
				}
			}
		} finally {
			executor.shutdownNow();
		}

		assertThat(jdbcTemplate.queryForObject("select count(*) from board_member where id = 10", Long.class))
			.isEqualTo(1L);
	}

	@Test
	@DisplayName("회원 서비스가 오류를 내면 예외를 밖으로 던지지 않고, 이미 채운 쪽은 남긴 채 멈춘다")
	void stopsQuietlyWhenMemberServiceFails() {
		when(port.fetch(0, 500)).thenReturn(page(true, 20, replica(20, "일", false, false, T1)));
		when(port.fetch(20, 500)).thenThrow(new RestClientException("회원 서비스 꺼짐"));

		assertThatCode(backfill::run).doesNotThrowAnyException();

		assertThat(memberRepository.findById(20L)).isPresent();
		verify(port, times(2)).fetch(anyLong(), anyInt());
	}

	@Test
	@DisplayName("회원 서비스가 처음부터 안 되면 아무것도 넣지 않고 조용히 끝난다")
	void stopsQuietlyWhenFirstCallFails() {
		when(port.fetch(0, 500)).thenThrow(new RestClientException("연결 거부"));
		long before = memberRepository.count();

		assertThatCode(backfill::run).doesNotThrowAnyException();

		assertThat(memberRepository.count()).isEqualTo(before);
	}

	@Test
	@DisplayName("hasNext가 true인데 nextAfterId가 늘지 않으면 무한 반복하지 않고 멈춘다")
	void stopsWhenCursorDoesNotAdvance() {
		when(port.fetch(0, 500)).thenReturn(page(true, 0, replica(10, "멈춤", false, false, T1)));

		assertThatCode(backfill::run).doesNotThrowAnyException();

		verify(port, times(1)).fetch(anyLong(), anyInt());
	}

	@Test
	@DisplayName("받을 회원이 없으면 한 번만 부르고 끝난다")
	void emptyFirstPage() {
		when(port.fetch(0, 500)).thenReturn(page(false, 0));

		backfill.run();

		verify(port, times(1)).fetch(anyLong(), anyInt());
	}

	private static void awaitThen(CountDownLatch start, Runnable action) {
		try {
			start.await();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return;
		}
		action.run();
	}

	private static MemberReplica replica(long id, String nickname, boolean seller, boolean withdrawn,
		LocalDateTime modifyDate) {
		return new MemberReplica(id, nickname, seller, withdrawn, modifyDate);
	}

	private static MemberReplicaPage page(boolean hasNext, long nextAfterId, MemberReplica... members) {
		return new MemberReplicaPage(List.of(members), hasNext, nextAfterId);
	}
}
