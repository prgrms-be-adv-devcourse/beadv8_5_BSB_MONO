package com.bukang.member.recordcert.in;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.recordcert.domain.SubmissionStatus;
import com.bukang.member.support.RecordCertSellerFixture;

/**
 * 판매자 기록증 심사 화면 API (BUK-276)
 * - 심사 필요 건수: 승인된 판매자의 제출에 저장된 판매자 회원 ID로 센다. 판매자가 아니면 403
 * - 승인 불가 메모는 200자까지
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({InMemoryIdentityVerificationStore.Config.class, RecordCertSellerFixture.class})
class ApiV1SellerRecordCertTest {
	private static final String SELLER = "/api/v1/member/seller";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private RecordCertSellerFixture fixture;

	@AfterEach
	void tearDown() {
		fixture.clear();
	}

	@Test
	@DisplayName("심사 필요 건수는 내 대회들의 심사 필요 제출만 센다")
	void countsOnlyMyNeedsReview() throws Exception {
		long sellerId = fixture.approvedSeller();
		fixture.submission(sellerId, 1, SubmissionStatus.NEEDS_REVIEW);
		fixture.submission(sellerId, 2, SubmissionStatus.NEEDS_REVIEW);
		fixture.submission(sellerId, 1, SubmissionStatus.APPROVED);
		fixture.submission(sellerId, 1, SubmissionStatus.CHECKING);
		fixture.submission(fixture.approvedSeller(), 3, SubmissionStatus.NEEDS_REVIEW);

		mockMvc.perform(get(SELLER + "/record-certs/review-count").header(MemberIdHeader.NAME, sellerId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.waitingCount").value(2));
	}

	@Test
	@DisplayName("승인된 판매자가 아니면 심사 필요 건수를 볼 수 없다 (403)")
	void notSellerCannotCount() throws Exception {
		long memberId = fixture.member();

		mockMvc.perform(get(SELLER + "/record-certs/review-count").header(MemberIdHeader.NAME, memberId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("RECORD_CERT_NOT_SELLER"));
	}

	@Test
	@DisplayName("승인 불가 메모는 200자까지 받는다")
	void denyMemoUpTo200() throws Exception {
		long sellerId = fixture.approvedSeller();
		long tooLong = fixture.submission(sellerId, 1, SubmissionStatus.NEEDS_REVIEW);
		long fits = fixture.submission(sellerId, 1, SubmissionStatus.NEEDS_REVIEW);

		mockMvc.perform(deny(sellerId, tooLong, "가".repeat(201)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("메모는 200자 이하로 써 주세요."));
		mockMvc.perform(deny(sellerId, fits, "가".repeat(200)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("DENIED"))
			.andExpect(jsonPath("$.data.denyReason").value("OTHER"));
	}

	@Test
	@DisplayName("승인 불가 사유와 판매자 메모는 참가자의 내 제출 목록에 보인다")
	void participantSeesDenyReason() throws Exception {
		long sellerId = fixture.approvedSeller();
		long submissionId = fixture.submission(sellerId, 1, SubmissionStatus.NEEDS_REVIEW);
		mockMvc.perform(deny(sellerId, submissionId, "  넷타임이 잘렸어요  ")).andExpect(status().isOk());

		// 픽스처의 제출은 회원 7이 냈다
		mockMvc.perform(get("/api/v1/member/record-certs").header(MemberIdHeader.NAME, 7))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].status").value("DENIED"))
			.andExpect(jsonPath("$.data[0].denyReason").value("OTHER"))
			.andExpect(jsonPath("$.data[0].denyMemo").value("넷타임이 잘렸어요"));
	}

	private RequestBuilder deny(long memberId, long submissionId, String memo) {
		return post(SELLER + "/record-certs/{id}/deny", submissionId)
			.header(MemberIdHeader.NAME, memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"OTHER\",\"memo\":\"" + memo + "\"}");
	}
}
