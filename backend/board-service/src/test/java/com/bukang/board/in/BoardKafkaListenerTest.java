package com.bukang.board.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;

import com.bukang.board.domain.BoardMember;
import com.bukang.board.support.BoardTestBase;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.member.event.MemberJoinedEvent;
import com.bukang.common.shared.member.event.MemberModifiedEvent;
import com.bukang.common.shared.member.event.MemberWithdrawnEvent;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.common.shared.seller.event.SellerChangedEvent;

/**
 * 회원·판매자 이벤트를 받아 회원 복사본(BOARD_MEMBER)에 반영하는지 확인한다.
 * 같은 메시지는 한 번만, 옛 수정 시각의 이벤트는 버리고, 탈퇴는 되돌아가지 않는다
 */
class BoardKafkaListenerTest extends BoardTestBase {
	private static final long NEW_MEMBER = 50L;
	private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 10, 9, 0, 0);

	@Autowired
	private BoardKafkaListener listener;

	@Test
	@DisplayName("받는 토픽 이름이 common KafkaTopics의 회원·판매자 토픽과 같다 (보내는 쪽과 받는 쪽 계약)")
	void listensToCommonTopics() {
		List<String> topics = Arrays.stream(BoardKafkaListener.class.getDeclaredMethods())
			.map(method -> method.getAnnotation(KafkaListener.class))
			.filter(java.util.Objects::nonNull)
			.flatMap(annotation -> Arrays.stream(annotation.topics()))
			.toList();

		assertThat(topics).containsExactlyInAnyOrder(KafkaTopics.MEMBER_MEMBER_JOINED,
			KafkaTopics.MEMBER_MODIFIED, KafkaTopics.MEMBER_WITHDRAWN, KafkaTopics.SELLER_CHANGED);
		assertThat(Arrays.stream(BoardKafkaListener.class.getDeclaredMethods())
			.map(Method::getName)).contains("handleMemberJoined", "handleMemberModified",
			"handleMemberWithdrawn", "handleSellerChanged");
	}

	@Test
	@DisplayName("가입 이벤트로 회원 복사본을 만든다 (판매자 아님, 탈퇴 아님)")
	void joinCreatesMember() {
		listener.handleMemberJoined(joined(NEW_MEMBER, "새러너", T0), "m-1");

		BoardMember member = memberRepository.findById(NEW_MEMBER).orElseThrow();
		assertThat(member.getNickname()).isEqualTo("새러너");
		assertThat(member.isSellerApproved()).isFalse();
		assertThat(member.isWithdrawn()).isFalse();
	}

	@Test
	@DisplayName("같은 message-id는 한 번만 처리한다 (두 번째는 내용이 달라도 버림)")
	void sameMessageIdOnce() {
		listener.handleMemberJoined(joined(NEW_MEMBER, "처음", T0), "m-1");
		listener.handleMemberModified(modified(NEW_MEMBER, "바꾼", T0.plusMinutes(1)), "m-2");
		listener.handleMemberModified(modified(NEW_MEMBER, "또 바꾼", T0.plusMinutes(2)), "m-2");

		assertThat(memberRepository.findById(NEW_MEMBER).orElseThrow().getNickname()).isEqualTo("바꾼");
		assertThat(inboxRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("인박스에 메시지 ID, 컨슈머 그룹(board), 받은 토픽이 남는다")
	void inboxRecordsTopic() {
		listener.handleMemberJoined(joined(NEW_MEMBER, "처음", T0), "m-1");

		assertThat(inboxRepository.findAll()).singleElement().satisfies(event -> {
			assertThat(event.getMessageId()).isEqualTo("m-1");
			assertThat(event.getConsumerGroup()).isEqualTo("board");
			assertThat(event.getTopic()).isEqualTo(KafkaTopics.MEMBER_MEMBER_JOINED);
		});
	}

	@Test
	@DisplayName("message-id 헤더가 없으면 인박스 없이 처리한다")
	void withoutMessageId() {
		listener.handleMemberJoined(joined(NEW_MEMBER, "처음", T0), null);

		assertThat(memberRepository.findById(NEW_MEMBER)).isPresent();
		assertThat(inboxRepository.count()).isZero();
	}

	@Test
	@DisplayName("수정 이벤트가 가입 이벤트보다 먼저 와도 닉네임이 되돌아가지 않는다 (옛 수정 시각은 버림)")
	void staleJoinAfterModifyIsDropped() {
		listener.handleMemberModified(modified(NEW_MEMBER, "바꾼 닉네임", T0.plusMinutes(5)), "m-2");
		listener.handleMemberJoined(joined(NEW_MEMBER, "처음 닉네임", T0), "m-1");

		assertThat(memberRepository.findById(NEW_MEMBER).orElseThrow().getNickname()).isEqualTo("바꾼 닉네임");
	}

	@Test
	@DisplayName("더 늦은 수정 시각의 이벤트는 반영한다")
	void newerModifyApplied() {
		listener.handleMemberJoined(joined(NEW_MEMBER, "처음", T0), "m-1");
		listener.handleMemberModified(modified(NEW_MEMBER, "바꿈", T0.plusMinutes(5)), "m-2");

		assertThat(memberRepository.findById(NEW_MEMBER).orElseThrow().getNickname()).isEqualTo("바꿈");
	}

	@Test
	@DisplayName("판매자 승인 이벤트를 받으면 판매자 표시가 켜지고, 반려로 바뀌면 꺼진다 (지금 승인 상태를 따름)")
	void sellerFlagFollowsStatus() throws Exception {
		long postId = newPost(ME, "글");

		listener.handleSellerChanged(seller(ME, SellerStatus.APPROVED), "s-1");
		getPost(null, postId).andExpect(jsonPath("$.data.writer.seller").value(true));

		listener.handleSellerChanged(seller(ME, SellerStatus.REJECTED), "s-2");
		getPost(null, postId).andExpect(jsonPath("$.data.writer.seller").value(false));
	}

	@Test
	@DisplayName("승인 대기 상태의 판매자 이벤트는 판매자 표시를 켜지 않는다")
	void pendingSellerIsNotSeller() {
		listener.handleSellerChanged(seller(ME, SellerStatus.PENDING), "s-1");

		assertThat(memberRepository.findById(ME).orElseThrow().isSellerApproved()).isFalse();
	}

	@Test
	@DisplayName("판매자 이벤트가 가입 이벤트보다 먼저 와도 판매자 여부를 담은 복사본을 만들고, 늦게 온 가입 이벤트가 지우지 않는다")
	void sellerBeforeJoin() {
		listener.handleSellerChanged(seller(NEW_MEMBER, SellerStatus.APPROVED), "s-1");

		assertThat(memberRepository.findById(NEW_MEMBER).orElseThrow().isSellerApproved()).isTrue();

		listener.handleMemberJoined(joined(NEW_MEMBER, "새러너", T0), "m-1");

		BoardMember member = memberRepository.findById(NEW_MEMBER).orElseThrow();
		assertThat(member.getNickname()).isEqualTo("새러너");
		assertThat(member.isSellerApproved()).isTrue();
		assertThat(member.getCreateDate()).isEqualTo(T0);
	}

	@Test
	@DisplayName("판매자 이벤트의 시각이 저장된 것보다 옛것이면 버린다 (승인 뒤에 늦게 온 옛 반려)")
	void staleSellerEventIsDropped() {
		listener.handleSellerChanged(sellerAt(ME, SellerStatus.APPROVED, T0.plusDays(1)), "s-1");
		listener.handleSellerChanged(sellerAt(ME, SellerStatus.REJECTED, T0), "s-2");

		assertThat(memberRepository.findById(ME).orElseThrow().isSellerApproved()).isTrue();

		listener.handleSellerChanged(sellerAt(ME, SellerStatus.REJECTED, T0.plusDays(2)), "s-3");

		assertThat(memberRepository.findById(ME).orElseThrow().isSellerApproved()).isFalse();
	}

	@Test
	@DisplayName("탈퇴하면 글은 남고 글쓴이가 탈퇴한 회원으로 바뀌며 닉네임은 응답에 나오지 않는다")
	void withdrawnWriter() throws Exception {
		long postId = newPost(ME, "글");
		newComment(OTHER, postId, "댓글", null);

		listener.handleMemberWithdrawn(withdrawn(ME, T0), "w-1");

		getPost(null, postId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.title").value("글"))
			.andExpect(jsonPath("$.data.writer.withdrawn").value(true))
			.andExpect(jsonPath("$.data.writer.seller").value(false))
			.andExpect(jsonPath("$.data.writer.nickname").value("탈퇴한 회원"));
		listener.handleMemberWithdrawn(withdrawn(OTHER, T0), "w-2");
		getComments(null, postId).andExpect(jsonPath("$.data[0].writer.withdrawn").value(true))
			.andExpect(jsonPath("$.data[0].writer.nickname").value("탈퇴한 회원"));
	}

	@Test
	@DisplayName("탈퇴한 글쓴이가 판매자였어도 탈퇴 뒤에는 판매자 표시를 보이지 않는다")
	void withdrawnSellerHidden() throws Exception {
		long postId = newPost(ME, "글");
		listener.handleSellerChanged(seller(ME, SellerStatus.APPROVED), "s-1");

		listener.handleMemberWithdrawn(withdrawn(ME, T0), "w-1");

		getPost(null, postId).andExpect(jsonPath("$.data.writer.seller").value(false));
	}

	@Test
	@DisplayName("탈퇴 이벤트가 가입 이벤트보다 먼저 와도 탈퇴 상태가 유지된다")
	void withdrawBeforeJoin() {
		listener.handleMemberWithdrawn(withdrawn(NEW_MEMBER, T0.plusMinutes(10)), "w-1");
		listener.handleMemberJoined(joined(NEW_MEMBER, "처음", T0), "m-1");

		BoardMember member = memberRepository.findById(NEW_MEMBER).orElseThrow();
		assertThat(member.isWithdrawn()).isTrue();
		assertThat(member.getNickname()).isNotEqualTo("처음");
	}

	@Test
	@DisplayName("탈퇴한 회원에게 늦게 온 수정 이벤트가 와도 탈퇴는 풀리지 않는다")
	void modifyAfterWithdrawKeepsWithdrawn() {
		listener.handleMemberJoined(joined(NEW_MEMBER, "처음", T0), "m-1");
		listener.handleMemberWithdrawn(withdrawn(NEW_MEMBER, T0.plusMinutes(1)), "w-1");
		listener.handleMemberModified(modified(NEW_MEMBER, "탈퇴한 회원", T0.plusMinutes(1)), "m-2");

		assertThat(memberRepository.findById(NEW_MEMBER).orElseThrow().isWithdrawn()).isTrue();
	}

	@Test
	@DisplayName("깨진 JSON은 예외 없이 버린다 (다시 받아도 같은 결과라 재시도하지 않는다)")
	void brokenMessageIsDropped() {
		assertThatCode(() -> listener.handleMemberJoined("{not json", "m-1")).doesNotThrowAnyException();
		assertThatCode(() -> listener.handleSellerChanged("{\"status\":\"UNKNOWN\"}", "s-1"))
			.doesNotThrowAnyException();

		assertThat(memberRepository.count()).isEqualTo(2);
		assertThat(inboxRepository.count()).isZero();
	}

	private String joined(long id, String nickname, LocalDateTime modifyDate) {
		return jsonConverter.toJson(new MemberJoinedEvent(id, T0, modifyDate, nickname, false, "e-" + id));
	}

	private String modified(long id, String nickname, LocalDateTime modifyDate) {
		return jsonConverter.toJson(new MemberModifiedEvent(id, T0, modifyDate, nickname, "e-" + id));
	}

	private String withdrawn(long id, LocalDateTime at) {
		return jsonConverter.toJson(new MemberWithdrawnEvent("e-" + id, id, at));
	}

	private String seller(long memberId, SellerStatus status) {
		return sellerAt(memberId, status, T0);
	}

	private String sellerAt(long memberId, SellerStatus status, LocalDateTime reviewedAt) {
		return jsonConverter.toJson(new SellerChangedEvent(1L, memberId, SellerType.INDIVIDUAL, null, null, "대표",
			status, T0.minusDays(1), reviewedAt));
	}
}
