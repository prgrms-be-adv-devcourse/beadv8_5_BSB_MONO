package com.bukang.inquiry.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryStatus;
import com.bukang.inquiry.support.InquiryTestBase;

/**
 * 설계 9장: 보낸 회원은 답변 대기일 때만 문의를 고치고 지울 수 있다. 지우기는 「지움」 표시만 하고 행은 남긴다.
 * 고치기·지우기·답변은 모두 「답변 대기이고 지우지 않은 행만」 바꾸는 조건부 UPDATE라서 겹쳐도 한쪽만 성공한다
 */
class InquiryEditDeleteTest extends InquiryTestBase {
	private static final String URL = BASE + "/inquiries/";

	@Test
	@DisplayName("보낸 회원은 답변 대기인 문의의 제목과 본문을 고친다")
	void edit() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);

		patchAs(URL + inquiry.getId(), ME, editJson("새 제목", "새 본문"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.title").value("새 제목"))
			.andExpect(jsonPath("$.data.body").value("새 본문"))
			.andExpect(jsonPath("$.data.status").value("WAITING"));

		Inquiry saved = inquiryRepository.findById(inquiry.getId()).orElseThrow();
		assertThat(saved.getTitle()).isEqualTo("새 제목");
		assertThat(saved.getBody()).isEqualTo("새 본문");
		assertThat(saved.getType()).isEqualTo(inquiry.getType());
		assertThat(saved.getOrderId()).isEqualTo(ORDER);
	}

	@Test
	@DisplayName("남의 문의·없는 문의는 고칠 수 없다 (404), 내용은 그대로다")
	void editNotMine() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);

		patchAs(URL + inquiry.getId(), OTHER, editJson("가로채기", "본문"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
		patchAs(URL + "123456", ME, editJson("제목", "본문")).andExpect(status().isNotFound());

		assertThat(inquiryRepository.findById(inquiry.getId()).orElseThrow().getTitle()).isEqualTo(inquiry.getTitle());
	}

	@Test
	@DisplayName("답변이 달린 문의는 고치거나 지울 수 없다 (409), 내용과 지움 표시는 그대로다")
	void answeredIsLocked() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);
		postAs(BASE + "/seller/inquiries/" + inquiry.getId() + "/answer", SELLER, answerJson("답"))
			.andExpect(status().isOk());

		patchAs(URL + inquiry.getId(), ME, editJson("늦은 수정", "본문"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INQUIRY_ALREADY_ANSWERED"));
		deleteAs(URL + inquiry.getId(), ME)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INQUIRY_ALREADY_ANSWERED"));

		Inquiry saved = inquiryRepository.findById(inquiry.getId()).orElseThrow();
		assertThat(saved.getTitle()).isEqualTo(inquiry.getTitle());
		assertThat(saved.getDeletedAt()).isNull();
		assertThat(saved.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
	}

	@ParameterizedTest
	@DisplayName("고칠 때도 빈 제목·빈 본문·제목 101자·본문 1001자는 400이다")
	@MethodSource("invalidEdits")
	void editValidation(String title, String body) throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);

		patchAs(URL + inquiry.getId(), ME, editJson(title, body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertThat(inquiryRepository.findById(inquiry.getId()).orElseThrow().getTitle()).isEqualTo(inquiry.getTitle());
	}

	static Stream<Arguments> invalidEdits() {
		return Stream.of(Arguments.of(" ", "본문"), Arguments.of("제목", " "),
			Arguments.of("가".repeat(101), "본문"), Arguments.of("제목", "나".repeat(1001)));
	}

	@Test
	@DisplayName("지우면 지움 표시만 하고 행은 남기며, 모든 목록·상세·답변·고치기·지우기가 404가 된다")
	void deleteHidesEverywhere() throws Exception {
		Inquiry sellerOwned = saveSellerInquiry(ME, RACE, SELLER);
		Inquiry adminOwned = saveAdminInquiry(ME, RACE, SELLER);

		deleteAs(URL + sellerOwned.getId(), ME).andExpect(status().isOk());
		deleteAs(URL + adminOwned.getId(), ME).andExpect(status().isOk());

		assertThat(inquiryRepository.count()).isEqualTo(2);
		assertThat(inquiryRepository.findById(sellerOwned.getId()).orElseThrow().getDeletedAt()).isNotNull();
		getAs(URL + "me", ME).andExpect(jsonPath("$.data.inquiries.length()").value(0));
		getAs(URL + sellerOwned.getId(), ME).andExpect(status().isNotFound());
		getAs(BASE + "/seller/inquiries", SELLER).andExpect(jsonPath("$.data.inquiries.length()").value(0));
		getAs(BASE + "/admin/inquiries", ADMIN).andExpect(jsonPath("$.data.inquiries.length()").value(0));
		getAs(BASE + "/seller/inquiries/" + sellerOwned.getId(), SELLER).andExpect(status().isNotFound());
		getAs(BASE + "/admin/inquiries/" + adminOwned.getId(), ADMIN).andExpect(status().isNotFound());
		postAs(BASE + "/seller/inquiries/" + sellerOwned.getId() + "/answer", SELLER, answerJson("답"))
			.andExpect(status().isNotFound());
		postAs(BASE + "/admin/inquiries/" + adminOwned.getId() + "/answer", ADMIN, answerJson("답"))
			.andExpect(status().isNotFound());
		patchAs(URL + sellerOwned.getId(), ME, editJson("제목", "본문")).andExpect(status().isNotFound());
		deleteAs(URL + sellerOwned.getId(), ME).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("남의 문의·없는 문의는 지울 수 없다 (404)")
	void deleteNotMine() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);

		deleteAs(URL + inquiry.getId(), OTHER).andExpect(status().isNotFound());
		deleteAs(URL + "123456", ME).andExpect(status().isNotFound());

		assertThat(inquiryRepository.findById(inquiry.getId()).orElseThrow().getDeletedAt()).isNull();
	}

	@Test
	@DisplayName("판매자가 보낸 문의도 보낸 판매자가 답변 전에 지울 수 있다")
	void deleteSellerSupport() throws Exception {
		Inquiry support = saveSellerSupportInquiry(APPROVED_SELLER);

		deleteAs(URL + support.getId(), APPROVED_SELLER).andExpect(status().isOk());

		getAs(URL + "me", APPROVED_SELLER).andExpect(jsonPath("$.data.inquiries.length()").value(0));
	}

	@RepeatedTest(5)
	@DisplayName("지우기와 답변이 동시에 오면 한쪽만 성공한다 (답변 200·지우기 409, 또는 지우기 200·답변 404)")
	void deleteAndAnswerAtOnce() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);

		List<Integer> statuses = atOnce(
			() -> deleteAs(URL + inquiry.getId(), ME).andReturn().getResponse().getStatus(),
			() -> postAs(BASE + "/seller/inquiries/" + inquiry.getId() + "/answer", SELLER, answerJson("답"))
				.andReturn().getResponse().getStatus());

		Inquiry saved = inquiryRepository.findById(inquiry.getId()).orElseThrow();
		int delete = statuses.get(0);
		int answer = statuses.get(1);
		if (answer == 200) {
			assertThat(delete).isEqualTo(409);
			assertThat(saved.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
			assertThat(saved.getDeletedAt()).isNull();
		} else {
			assertThat(delete).isEqualTo(200);
			assertThat(answer).isIn(404, 409);
			assertThat(saved.getDeletedAt()).isNotNull();
			assertThat(saved.getStatus()).isEqualTo(InquiryStatus.WAITING);
			assertThat(saved.getAnswer()).isNull();
		}
	}

	@RepeatedTest(5)
	@DisplayName("고치기와 답변이 동시에 와도 답변 뒤에는 고쳐지지 않는다 (고치기가 409면 제목은 그대로)")
	void editAndAnswerAtOnce() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);

		List<Integer> statuses = atOnce(
			() -> patchAs(URL + inquiry.getId(), ME, editJson("새 제목", "새 본문")).andReturn().getResponse().getStatus(),
			() -> postAs(BASE + "/seller/inquiries/" + inquiry.getId() + "/answer", SELLER, answerJson("답"))
				.andReturn().getResponse().getStatus());

		Inquiry saved = inquiryRepository.findById(inquiry.getId()).orElseThrow();
		assertThat(statuses.get(1)).isEqualTo(200);
		assertThat(statuses.get(0)).isIn(200, 409);
		assertThat(saved.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
		assertThat(saved.getTitle()).isEqualTo(statuses.get(0) == 200 ? "새 제목" : inquiry.getTitle());
	}

	@RepeatedTest(5)
	@DisplayName("같은 문의를 동시에 두 번 지우면 하나만 성공하고 나머지는 404다")
	void deleteTwiceAtOnce() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);

		List<Integer> statuses = atOnce(
			() -> deleteAs(URL + inquiry.getId(), ME).andReturn().getResponse().getStatus(),
			() -> deleteAs(URL + inquiry.getId(), ME).andReturn().getResponse().getStatus());

		assertThat(statuses).containsExactlyInAnyOrder(200, 404);
	}

	// 두 요청이 같은 순간에 출발하도록 장벽을 세운다
	@SafeVarargs
	private List<Integer> atOnce(Callable<Integer>... requests) throws Exception {
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
