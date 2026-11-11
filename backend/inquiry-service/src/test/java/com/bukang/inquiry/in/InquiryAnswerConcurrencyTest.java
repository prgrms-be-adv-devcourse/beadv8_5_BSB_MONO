package com.bukang.inquiry.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryStatus;
import com.bukang.inquiry.support.InquiryTestBase;

/**
 * 같은 문의에 두 명이 동시에 답하면 하나만 저장되는지 확인한다.
 * 답변 저장이 「답변 대기인 행만」 바꾸는 조건부 UPDATE라서, 늦은 쪽은 0행이 바뀌어 409가 된다
 */
class InquiryAnswerConcurrencyTest extends InquiryTestBase {
	@RepeatedTest(5)
	@DisplayName("관리자 둘이 동시에 답하면 하나는 200, 하나는 409이고 먼저 저장된 답변 하나만 남는다")
	void twoAdminsAnswerAtOnce() throws Exception {
		memberAdminPort.admin(ADMIN + 1);
		Inquiry inquiry = saveAdminInquiry(ME, RACE, SELLER);
		String url = BASE + "/admin/inquiries/" + inquiry.getId() + "/answer";

		List<Integer> statuses = answerAtOnce(
			() -> postAs(url, ADMIN, answerJson("관리자 20의 답변")).andReturn().getResponse().getStatus(),
			() -> postAs(url, ADMIN + 1, answerJson("관리자 21의 답변")).andReturn().getResponse().getStatus());

		assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		Inquiry saved = inquiryRepository.findById(inquiry.getId()).orElseThrow();
		assertThat(saved.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
		// 답변 본문과 답한 회원이 같은 쪽의 것이어야 한다 (두 답변이 섞이지 않는다)
		assertThat(saved.getAnswer()).isEqualTo("관리자 " + saved.getAnsweredBy() + "의 답변");
	}

	@RepeatedTest(5)
	@DisplayName("판매자가 같은 문의에 두 번 동시에 답해도 하나만 저장된다")
	void sellerAnswersTwiceAtOnce() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);
		String url = BASE + "/seller/inquiries/" + inquiry.getId() + "/answer";

		List<Integer> statuses = answerAtOnce(
			() -> postAs(url, SELLER, answerJson("첫째 답변")).andReturn().getResponse().getStatus(),
			() -> postAs(url, SELLER, answerJson("둘째 답변")).andReturn().getResponse().getStatus());

		assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		assertThat(inquiryRepository.findById(inquiry.getId()).orElseThrow().getAnswer())
			.isIn("첫째 답변", "둘째 답변");
	}

	// 두 요청이 같은 순간에 출발하도록 장벽을 세운다
	@SafeVarargs
	private List<Integer> answerAtOnce(Callable<Integer>... requests) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(requests.length);
		try {
			CyclicBarrier barrier = new CyclicBarrier(requests.length);
			List<Future<Integer>> futures = new ArrayList<>();
			for (Callable<Integer> request : requests) {
				futures.add(executor.submit(() -> {
					barrier.await();
					return request.call();
				}));
			}
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> future : futures) {
				statuses.add(future.get());
			}
			return statuses;
		} finally {
			executor.shutdownNow();
		}
	}
}
