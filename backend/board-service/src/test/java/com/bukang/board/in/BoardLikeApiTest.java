package com.bukang.board.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.board.app.BoardLikeUseCase;
import com.bukang.board.domain.BoardMember;
import com.bukang.board.support.BoardTestBase;

/**
 * 좋아요 누르기(PUT)·취소(DELETE). 좋아요 수는 DB가 직접 더하고 빼므로, 동시에 눌러도 하나도 사라지지 않는지 확인한다
 */
class BoardLikeApiTest extends BoardTestBase {
	@Autowired
	private BoardLikeUseCase boardLikeUseCase;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	@DisplayName("좋아요를 누르면 수가 1 늘고, 상세에 내가 눌렀다고 나온다")
	void like() throws Exception {
		long postId = newPost(ME, "글");

		putLike(OTHER, postId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.likeCount").value(1))
			.andExpect(jsonPath("$.data.liked").value(true));

		getPost(OTHER, postId).andExpect(jsonPath("$.data.likeCount").value(1))
			.andExpect(jsonPath("$.data.liked").value(true));
		getPost(ME, postId).andExpect(jsonPath("$.data.liked").value(false));
		getPost(null, postId).andExpect(jsonPath("$.data.liked").value(false));
	}

	@Test
	@DisplayName("두 번 눌러도 한 번만 센다 (같은 결과, 오류 없음)")
	void likeTwice() throws Exception {
		long postId = newPost(ME, "글");

		putLike(OTHER, postId).andExpect(status().isOk());
		putLike(OTHER, postId).andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(1));

		assertThat(likeRepository.count()).isEqualTo(1);
		assertThat(postRepository.findById(postId).orElseThrow().getLikeCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("취소하면 수가 1 줄고, 누른 적 없는 사람의 취소나 두 번째 취소는 수를 바꾸지 않는다")
	void unlike() throws Exception {
		long postId = newPost(ME, "글");
		putLike(OTHER, postId);
		putLike(ME, postId);

		deleteLike(OTHER, postId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.likeCount").value(1))
			.andExpect(jsonPath("$.data.liked").value(false));
		deleteLike(OTHER, postId).andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(1));

		assertThat(postRepository.findById(postId).orElseThrow().getLikeCount()).isEqualTo(1);
		assertThat(likeRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("로그인 없이 좋아요를 누르면 401")
	void likeNeedsLogin() throws Exception {
		long postId = newPost(ME, "글");

		putLike(null, postId).andExpect(status().isUnauthorized());
		deleteLike(null, postId).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("없는 글·지운 글·남의 숨겨진 글에는 좋아요를 누를 수 없다 (404)")
	void likeOnUnavailablePost() throws Exception {
		long deleted = newPost(ME, "지울 글");
		deletePost(ME, deleted);
		long hidden = newPost(ME, "숨길 글");
		hidePost(hidden);

		putLike(OTHER, 999999).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BOARD_POST_NOT_FOUND"));
		putLike(OTHER, deleted).andExpect(status().isNotFound());
		putLike(OTHER, hidden).andExpect(status().isNotFound());
		deleteLike(OTHER, hidden).andExpect(status().isNotFound());
		assertThat(likeRepository.count()).isZero();
	}

	@Test
	@DisplayName("같은 회원이 동시에 20번 눌러도 좋아요는 1개, 수도 1이다")
	void sameMemberConcurrently() throws Exception {
		long postId = newPost(ME, "글");

		runConcurrently(20, index -> boardLikeUseCase.like(OTHER, postId));

		assertThat(likeRepository.count()).isEqualTo(1);
		assertThat(postRepository.findById(postId).orElseThrow().getLikeCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("서로 다른 회원 20명이 동시에 눌러도 하나도 사라지지 않고 수가 20이다")
	void differentMembersConcurrently() throws Exception {
		long postId = newPost(ME, "글");
		LocalDateTime now = LocalDateTime.now();
		for (long id = 100; id < 120; id++) {
			memberRepository.save(new BoardMember(id, "러너" + id, now, now));
		}

		runConcurrently(20, index -> boardLikeUseCase.like(100L + index, postId));

		assertThat(likeRepository.count()).isEqualTo(20);
		assertThat(postRepository.findById(postId).orElseThrow().getLikeCount()).isEqualTo(20);
	}

	@Test
	@DisplayName("누르기와 취소가 섞여 동시에 와도 수는 남은 좋아요 행 수와 같다")
	void likeAndUnlikeConcurrently() throws Exception {
		long postId = newPost(ME, "글");
		for (long id = 100; id < 110; id++) {
			boardLikeUseCase.like(id, postId);
		}

		runConcurrently(20, index -> {
			if (index % 2 == 0) {
				boardLikeUseCase.unlike(100L + index / 2, postId);
			} else {
				boardLikeUseCase.like(200L + index, postId);
			}
		});

		assertThat(postRepository.findById(postId).orElseThrow().getLikeCount()).isEqualTo(likeRepository.count());
		assertThat(likeRepository.count()).isEqualTo(10);
	}

	@Test
	@DisplayName("좋아요 행과 수는 한 트랜잭션이라, 롤백하면 둘 다 없던 일이 된다")
	void rollbackUndoesBoth() throws Exception {
		long postId = newPost(ME, "글");

		transactionTemplate.executeWithoutResult(tx -> {
			boardLikeUseCase.like(OTHER, postId);
			tx.setRollbackOnly();
		});

		assertThat(likeRepository.count()).isZero();
		assertThat(postRepository.findById(postId).orElseThrow().getLikeCount()).isZero();
	}

	private void runConcurrently(int threads, ThrowingIntConsumer task) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			int index = i;
			futures.add(executor.submit(() -> {
				ready.countDown();
				go.await();
				task.accept(index);
				return null;
			}));
		}
		ready.await();
		go.countDown();
		for (Future<?> future : futures) {
			future.get(30, TimeUnit.SECONDS);
		}
		executor.shutdown();
	}

	@FunctionalInterface
	private interface ThrowingIntConsumer {
		void accept(int index) throws Exception;
	}
}
