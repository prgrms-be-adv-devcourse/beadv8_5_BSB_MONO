package com.bukang.board.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;

import com.bukang.board.domain.BoardMember;
import com.bukang.board.domain.BoardOutboxKafkaEvent;
import com.bukang.board.domain.BoardPostChangedEvent;
import com.bukang.board.domain.OutboxStatus;
import com.bukang.board.out.member.MemberAdminPort;
import com.bukang.board.support.BoardTestBase;
import com.bukang.common.shared.kafka.KafkaTopics;

/**
 * 실패 경우 점검표 중 "트랜잭션 경계": 글이 바뀌면 같은 트랜잭션에서 색인 아웃박스 행이 남고,
 * 글 저장이 거절되면 행이 남지 않는다. 숨김·되살리기·관리자 삭제도 남긴다
 */
class BoardOutboxAppendTest extends BoardTestBase {
	private static final String REPORTS = "/api/v1/board/admin/reports";
	private static final long ADMIN = 99L;

	@Autowired
	private BoardOutboxKafkaAppendUseCase appendUseCase;
	@MockitoBean
	private MemberAdminPort memberAdminPort;

	@BeforeEach
	void adminIs99() {
		when(memberAdminPort.isAdmin(ADMIN)).thenReturn(true);
	}

	private List<BoardOutboxKafkaEvent> events() {
		return outboxRepository.findAll().stream()
			.sorted(java.util.Comparator.comparing(BoardOutboxKafkaEvent::getId)).toList();
	}

	private void hideByReports(long postId) throws Exception {
		for (long id = 11; id < 16; id++) {
			reportPost(id, postId, "AD").andExpect(status().isCreated());
		}
	}

	@Test
	@DisplayName("글을 쓰면 색인 토픽으로 보낼 행이 PENDING으로 남고, 글 ID가 key이며 메시지 ID가 붙어 있다")
	void createAppends() throws Exception {
		long postId = newPost(ME, "한강 러닝");

		assertThat(events()).hasSize(1);
		BoardOutboxKafkaEvent event = events().getFirst();
		assertThat(event.getEventType()).isEqualTo(KafkaTopics.BOARD_POST_CHANGED);
		assertThat(event.getRefType()).isEqualTo("BoardPost");
		assertThat(event.getRefId()).isEqualTo(postId);
		assertThat(event.getMessageKey()).isEqualTo(String.valueOf(postId));
		assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(event.getMessageId()).startsWith("board-outbox-");
		assertThat(jsonConverter.fromJson(event.getPayload(), BoardPostChangedEvent.class).postId())
			.isEqualTo(postId);
	}

	@Test
	@DisplayName("고치기·지우기도 같은 글 ID로 행이 남고, 행마다 메시지 ID가 다르다")
	void updateAndDeleteAppend() throws Exception {
		long postId = newPost(ME, "처음");
		putPost(ME, postId, postBody("바꿈", "본문")).andExpect(status().isOk());
		deletePost(ME, postId).andExpect(status().isOk());

		List<BoardOutboxKafkaEvent> events = events();
		assertThat(events).hasSize(3);
		assertThat(events).allMatch(event -> event.getRefId() == postId);
		assertThat(events.stream().map(BoardOutboxKafkaEvent::getMessageId).distinct()).hasSize(3);
	}

	@Test
	@DisplayName("신고 5건으로 숨겨질 때 행이 남고, 5건 미만이면 남지 않는다")
	void hideAppends() throws Exception {
		long postId = newPost(ME, "광고 글");
		for (long id = 11; id < 15; id++) {
			reportPost(id, postId, "AD").andExpect(status().isCreated());
		}
		assertThat(events()).hasSize(1);

		reportPost(15L, postId, "AD").andExpect(status().isCreated());

		assertThat(events()).hasSize(2);
		assertThat(events().getLast().getRefId()).isEqualTo(postId);
	}

	@Test
	@DisplayName("관리자가 되살리거나 지워도 행이 남는다")
	void adminHandleAppends() throws Exception {
		long restored = newPost(ME, "되살릴 글");
		hideByReports(restored);
		long deleted = newPost(ME, "지울 글");
		hideByReports(deleted);
		int before = events().size();

		mockMvc.perform(as(post(REPORTS + "/posts/" + restored + "/restore"), ADMIN)).andExpect(status().isOk());
		mockMvc.perform(as(post(REPORTS + "/posts/" + deleted + "/delete"), ADMIN)).andExpect(status().isOk());

		List<BoardOutboxKafkaEvent> added = events().subList(before, events().size());
		assertThat(added).extracting(BoardOutboxKafkaEvent::getRefId).containsExactly(restored, deleted);
	}

	@Test
	@DisplayName("거절된 요청(도배 제한, 탈퇴 회원, 남의 글 고치기, 없는 글 지우기)은 행을 남기지 않는다")
	void rejectedWritesAppendNothing() throws Exception {
		long postId = newPost(OTHER, "남의 글");
		outboxRepository.deleteAll();

		putPost(ME, postId, postBody("가로채기", "본문")).andExpect(status().isForbidden());
		deletePost(ME, 123456L).andExpect(status().isNotFound());
		when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
		createPost(ME, postBody("연타", "본문")).andExpect(status().isTooManyRequests());
		memberRepository.save(BoardMember.withdrawnOf(ME, LocalDateTime.now()));
		createPost(ME, postBody("탈퇴 뒤", "본문")).andExpect(status().isForbidden());

		assertThat(events()).isEmpty();
	}

	@Test
	@DisplayName("색인 아웃박스는 글을 저장하는 트랜잭션 안에서만 남길 수 있다 (밖에서 부르면 예외)")
	void appendNeedsTransaction() {
		assertThatThrownBy(() -> appendUseCase.appendPostChanged(1L))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(events()).isEmpty();
	}
}
