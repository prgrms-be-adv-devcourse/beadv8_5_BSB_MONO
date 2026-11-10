package com.bukang.board.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.board.app.BoardPostUseCase;
import com.bukang.board.domain.BoardCategory;
import com.bukang.board.domain.BoardPost;
import com.bukang.board.domain.BoardPostStatus;
import com.bukang.board.dto.PostSaveRequestDto;
import com.bukang.board.support.BoardTestBase;
import com.bukang.common.shared.file.domain.FileType;
import com.bukang.common.shared.file.event.FileLinkRequestedEvent;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;

/**
 * 글 쓰기·상세·고치기·지우기. 권한(남의 글, 숨김, 지움), 글자 수, 도배 제한, 사진 연결 메시지를 확인한다
 */
class BoardPostApiTest extends BoardTestBase {
	@Autowired
	private BoardPostUseCase boardPostUseCase;
	@Autowired
	private TransactionTemplate transactionTemplate;

	// ---- 정상 흐름 ----

	@Test
	@DisplayName("글을 쓰면 201과 글 ID를 주고, 말머리를 안 고르면 자유다")
	void createDefaultsToFree() throws Exception {
		createPost(ME, postBody("첫 글", "안녕하세요")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.postId").isNumber());

		BoardPost saved = postRepository.findAll().getFirst();
		assertThat(saved.getCategory()).isEqualTo(BoardCategory.FREE);
		assertThat(saved.getMemberId()).isEqualTo(ME);
		assertThat(saved.getStatus()).isEqualTo(BoardPostStatus.VISIBLE);
		assertThat(saved.isEdited()).isFalse();
		assertThat(saved.getLikeCount()).isZero();
		assertThat(saved.getReportCount()).isZero();
	}

	@Test
	@DisplayName("글 상세는 로그인 없이 볼 수 있고, 판매자 표시·수정됨·탈퇴 여부·내가 누른 좋아요를 담는다")
	void detailShape() throws Exception {
		long postId = newPost(ME, "제목");

		getPost(null, postId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.postId").value(postId))
			.andExpect(jsonPath("$.data.title").value("제목"))
			.andExpect(jsonPath("$.data.content").value("본문"))
			.andExpect(jsonPath("$.data.category").value("FREE"))
			.andExpect(jsonPath("$.data.status").value("VISIBLE"))
			.andExpect(jsonPath("$.data.writer.nickname").value("러너하나"))
			.andExpect(jsonPath("$.data.writer.seller").value(false))
			.andExpect(jsonPath("$.data.writer.withdrawn").value(false))
			.andExpect(jsonPath("$.data.edited").value(false))
			.andExpect(jsonPath("$.data.likeCount").value(0))
			.andExpect(jsonPath("$.data.liked").value(false))
			.andExpect(jsonPath("$.data.mine").value(false));

		getPost(ME, postId).andExpect(jsonPath("$.data.mine").value(true));
	}

	@Test
	@DisplayName("없는 글 번호는 404 BOARD_POST_NOT_FOUND")
	void unknownPost() throws Exception {
		getPost(null, 999999).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BOARD_POST_NOT_FOUND"));
	}

	@Test
	@DisplayName("고치면 수정됨이 붙는다")
	void editMarksEdited() throws Exception {
		long postId = newPost(ME, "제목");

		putPost(ME, postId, body("title", "새 제목", "content", "새 본문", "category", "QUESTION"))
			.andExpect(status().isOk());

		getPost(null, postId).andExpect(jsonPath("$.data.title").value("새 제목"))
			.andExpect(jsonPath("$.data.category").value("QUESTION"))
			.andExpect(jsonPath("$.data.edited").value(true));
	}

	@Test
	@DisplayName("지우면 상세가 404이고 행은 지움 상태로 남는다 (소프트 삭제)")
	void deleteIsSoft() throws Exception {
		long postId = newPost(ME, "제목");

		deletePost(ME, postId).andExpect(status().isOk());

		getPost(null, postId).andExpect(status().isNotFound());
		assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(BoardPostStatus.DELETED);
	}

	@Test
	@DisplayName("지운 글은 다시 지우거나 고칠 수 없다 (404)")
	void deletedPostIsGone() throws Exception {
		long postId = newPost(ME, "제목");
		deletePost(ME, postId);

		deletePost(ME, postId).andExpect(status().isNotFound());
		putPost(ME, postId, postBody("x", "y")).andExpect(status().isNotFound());
	}

	// ---- 보안·악용 ----

	@Test
	@DisplayName("로그인 헤더 없이 글을 쓰거나 고치거나 지우면 401")
	void writeNeedsLogin() throws Exception {
		long postId = newPost(ME, "제목");

		createPost(null, postBody("제목", "본문")).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
		putPost(null, postId, postBody("제목", "본문")).andExpect(status().isUnauthorized());
		deletePost(null, postId).andExpect(status().isUnauthorized());
		assertThat(postRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("남의 글을 고치거나 지우면 403 BOARD_NOT_WRITER이고 글은 그대로다")
	void notWriter() throws Exception {
		long postId = newPost(ME, "제목");

		putPost(OTHER, postId, postBody("바꿈", "바꿈")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("BOARD_NOT_WRITER"));
		deletePost(OTHER, postId).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("BOARD_NOT_WRITER"));

		BoardPost post = postRepository.findById(postId).orElseThrow();
		assertThat(post.getTitle()).isEqualTo("제목");
		assertThat(post.getStatus()).isEqualTo(BoardPostStatus.VISIBLE);
	}

	@Test
	@DisplayName("신고로 숨겨진 글은 남에게 404(있다는 사실도 숨김), 쓴 사람에게는 상태가 숨김으로 보인다")
	void hiddenPostVisibility() throws Exception {
		long postId = newPost(ME, "제목");
		hidePost(postId);

		getPost(null, postId).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BOARD_POST_NOT_FOUND"));
		getPost(OTHER, postId).andExpect(status().isNotFound());
		putPost(OTHER, postId, postBody("a", "b")).andExpect(status().isNotFound());
		deletePost(OTHER, postId).andExpect(status().isNotFound());
		getPost(ME, postId).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("HIDDEN"));
	}

	@Test
	@DisplayName("숨겨진 내 글은 고칠 수 없고(409 BOARD_POST_HIDDEN) 지우기만 된다")
	void hiddenPostWriterCanOnlyDelete() throws Exception {
		long postId = newPost(ME, "제목");
		hidePost(postId);

		putPost(ME, postId, postBody("바꿈", "바꿈")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BOARD_POST_HIDDEN"));
		assertThat(postRepository.findById(postId).orElseThrow().getTitle()).isEqualTo("제목");

		deletePost(ME, postId).andExpect(status().isOk());
		assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(BoardPostStatus.DELETED);
	}

	@Test
	@DisplayName("제목 50자, 본문 5,000자를 넘기거나 비어 있으면 400이고 저장하지 않는다")
	void lengthLimits() throws Exception {
		createPost(ME, postBody(longText(51), "본문")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		createPost(ME, postBody("제목", longText(5001))).andExpect(status().isBadRequest());
		createPost(ME, postBody(" ", "본문")).andExpect(status().isBadRequest());
		createPost(ME, postBody("제목", "")).andExpect(status().isBadRequest());
		putPost(ME, newPost(ME, "원래"), postBody(longText(51), "본문")).andExpect(status().isBadRequest());

		createPost(ME, postBody(longText(50), longText(5000))).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("모르는 말머리는 400이다")
	void unknownCategory() throws Exception {
		createPost(ME, body("title", "제목", "content", "본문", "category", "NOTICE"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("사진 파일 ID가 6개 이상이거나 0 이하이면 400이다")
	void fileIdLimits() throws Exception {
		createPost(ME, body("title", "제목", "content", "본문", "fileIds", fileIds(1, 2, 3, 4, 5, 6)))
			.andExpect(status().isBadRequest());
		createPost(ME, body("title", "제목", "content", "본문", "fileIds", fileIds(0)))
			.andExpect(status().isBadRequest());
		assertThat(postRepository.count()).isZero();
	}

	// ---- 도배 제한 (Redis) ----

	@Test
	@DisplayName("글 도배 제한은 members:{회원ID}:board-post-cooldown 키로 1분을 건다")
	void postCooldownKey() throws Exception {
		createPost(ME, postBody("제목", "본문")).andExpect(status().isCreated());

		verify(valueOperations).setIfAbsent(eq("members:1:board-post-cooldown"), anyString(),
			eq(Duration.ofMinutes(1)));
	}

	@Test
	@DisplayName("1분 안에 또 쓰면 429 BOARD_TOO_FAST이고 글은 저장되지 않는다")
	void postTooFast() throws Exception {
		when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true, false);

		createPost(ME, postBody("첫 글", "본문")).andExpect(status().isCreated());
		createPost(ME, postBody("둘째 글", "본문")).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("BOARD_TOO_FAST"));

		assertThat(postRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("입력이 틀린 요청은 도배 제한 시간을 쓰지 않는다")
	void invalidRequestDoesNotConsumeCooldown() throws Exception {
		createPost(ME, postBody(longText(51), "본문")).andExpect(status().isBadRequest());

		verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
	}

	@Test
	@DisplayName("Redis가 멈추면 제한 없이 글쓰기를 통과시킨다")
	void redisDownAllowsPost() throws Exception {
		when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
			.thenThrow(new RedisConnectionFailureException("redis down"));

		createPost(ME, postBody("제목", "본문")).andExpect(status().isCreated());
		createPost(ME, postBody("제목2", "본문")).andExpect(status().isCreated());
	}

	// ---- 사진 연결 (커밋 뒤 Kafka) ----

	@Test
	@DisplayName("글을 저장한 뒤 race.filelink.requested로 사진 연결 요청을 보낸다 (refType은 글 엔티티 이름, 순서대로 sortNo)")
	void createSendsFileLink() throws Exception {
		String response = createPost(ME, body("title", "사진 글", "content", "본문", "fileIds", fileIds(11, 12)))
			.andReturn().getResponse().getContentAsString();

		FileLinkRequestedEvent event = sentFileLink();
		long newId = ((Number)((Map<?, ?>)jsonConverter.fromJson(response, Map.class)
			.get("data")).get("postId")).longValue();
		assertThat(event.ownerId()).isEqualTo(ME);
		assertThat(event.refs()).hasSize(1);
		FileLinkRequestedEvent.Ref ref = event.refs().getFirst();
		assertThat(ref.refType()).isEqualTo("BoardPost");
		assertThat(ref.refId()).isEqualTo(newId);
		assertThat(ref.files()).extracting(FileLinkRequestedEvent.Item::fileId).containsExactly(11L, 12L);
		assertThat(ref.files()).extracting(FileLinkRequestedEvent.Item::sortNo).containsExactly(0, 1);
		assertThat(ref.files()).extracting(FileLinkRequestedEvent.Item::fileType)
			.containsOnly(FileType.POST_IMAGE);
	}

	@Test
	@DisplayName("메시지에는 message-id 헤더가 붙는다 (받는 file-service 인박스가 중복을 거른다)")
	void fileLinkHasMessageId() throws Exception {
		createPost(ME, body("title", "사진 글", "content", "본문", "fileIds", fileIds(11))).andExpect(status().isCreated());

		ProducerRecord<String, String> record = sentRecord();
		assertThat(record.topic()).isEqualTo(KafkaTopics.RACE_FILE_LINK_REQUESTED);
		assertThat(new String(record.headers().lastHeader(KafkaMessageId.HEADER).value(), StandardCharsets.UTF_8))
			.isNotBlank();
	}

	@Test
	@DisplayName("사진 없이 쓴 글은 메시지를 보내지 않는다")
	void createWithoutFilesSendsNothing() throws Exception {
		createPost(ME, postBody("제목", "본문")).andExpect(status().isCreated());

		verify(kafkaTemplate, after(500).never()).send(any(ProducerRecord.class));
	}

	@Test
	@DisplayName("고칠 때 fileIds를 빼면 사진을 건드리지 않고, 빈 목록이면 사진을 모두 뗀다")
	void updateFileLinkSemantics() throws Exception {
		long postId = newPost(ME, "제목");

		putPost(ME, postId, postBody("새 제목", "본문")).andExpect(status().isOk());
		verify(kafkaTemplate, after(500).never()).send(any(ProducerRecord.class));

		putPost(ME, postId, body("title", "새 제목", "content", "본문", "fileIds", fileIds()))
			.andExpect(status().isOk());
		FileLinkRequestedEvent cleared = sentFileLink();
		assertThat(cleared.refs().getFirst().refId()).isEqualTo(postId);
		assertThat(cleared.refs().getFirst().files()).isEmpty();
	}

	@Test
	@DisplayName("롤백된 글쓰기는 사진 연결 메시지를 보내지 않는다 (커밋 뒤에만 보낸다)")
	void rolledBackCreateSendsNothing() {
		transactionTemplate.executeWithoutResult(tx -> {
			boardPostUseCase.create(ME, new PostSaveRequestDto(null, "제목", "본문", fileIds(11)));
			tx.setRollbackOnly();
		});

		assertThat(postRepository.count()).isZero();
		verify(kafkaTemplate, after(500).never()).send(any(ProducerRecord.class));
	}

	@Test
	@DisplayName("Kafka 전송이 실패해도 글쓰기는 성공한다 (사진만 빠진다)")
	void kafkaFailureDoesNotFailCreate() throws Exception {
		when(kafkaTemplate.send(any(ProducerRecord.class))).thenThrow(new IllegalStateException("kafka down"));

		createPost(ME, body("title", "제목", "content", "본문", "fileIds", fileIds(11)))
			.andExpect(status().isCreated());

		assertThat(postRepository.count()).isEqualTo(1);
	}

	@SuppressWarnings("unchecked")
	private ProducerRecord<String, String> sentRecord() {
		ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate, timeout(2000)).send(captor.capture());
		return captor.getValue();
	}

	private FileLinkRequestedEvent sentFileLink() {
		return jsonConverter.fromJson(sentRecord().value(), FileLinkRequestedEvent.class);
	}
}
