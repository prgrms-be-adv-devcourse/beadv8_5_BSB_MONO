package com.bukang.ai.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.ai.domain.AiScreen;
import com.bukang.ai.out.ops.HttpOpsAdapter;
import com.bukang.ai.out.ops.OpsPort;

/**
 * 각 서비스의 화면용 API와 계약(경로, 회원 번호 헤더, 읽는 칸)이 맞는지 확인한다
 * 응답 예시는 각 서비스 DTO(SellerKitDto, PayoutSummaryDto, SellerPayoutHoldDto, InquiryManagePageDto, SellerDto)의 칸 이름이다
 */
class HttpOpsAdapterTest {
	private MockRestServiceServer server;
	private HttpOpsAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://svc");
		server = MockRestServiceServer.bindTo(builder).build();
		RestClient client = builder.build();
		adapter = new HttpOpsAdapter(client, client, client, client, client);
	}

	@Test
	@DisplayName("키트: 건수와 배송 준비 목록을 회원 번호 헤더로 묻고, 배송지는 읽지 않는다")
	void kits() {
		server.expect(requestTo("http://svc/api/v1/order/seller/kits/summary"))
			.andExpect(header("X-Member-Id", "3"))
			.andRespond(ok("{\"preparing\":7,\"shipping\":1,\"delivered\":2,\"beforeRefundDeadline\":3}"));
		server.expect(requestTo("http://svc/api/v1/order/seller/kits?status=PREPARING&page=0&size=5"))
			.andExpect(header("X-Member-Id", "3"))
			.andRespond(ok("{\"kits\":[{\"orderItemId\":12,\"orderNo\":\"ORD-1\",\"courseName\":\"10km\","
				+ "\"participantName\":\"김민지\",\"shippingAddress\":{\"phone\":\"01012345678\",\"address\":\"서울\"}}],"
				+ "\"page\":0,\"size\":5,\"totalElements\":7,\"totalPages\":2}"));

		assertThat(adapter.kitSummary(3L)).isEqualTo(new OpsPort.KitSummary(7, 3));
		assertThat(adapter.preparingKits(3L, 5))
			.isEqualTo(new OpsPort.KitPage(List.of(new OpsPort.Kit("ORD-1", "김민지", "10km")), 7));
		server.verify();
	}

	@Test
	@DisplayName("정산: 요약과 지급보류 내역")
	void payout() {
		server.expect(requestTo("http://svc/api/v1/payout/seller/summary")).andExpect(header("X-Member-Id", "3"))
			.andRespond(ok("{\"nextPayDate\":\"2026-11-20\",\"scheduledPayoutAmount\":1250000,\"heldCount\":1}"));
		server.expect(requestTo("http://svc/api/v1/payout/seller/holds?from=2026-10-12&to=2026-11-11"))
			.andExpect(header("X-Member-Id", "3"))
			.andRespond(ok("[{\"id\":1,\"holdCode\":\"H-1\",\"payoutId\":9,\"payoutCode\":\"P-9\",\"raceId\":4,"
				+ "\"raceName\":\"한강 나이트런\",\"payoutAmount\":120000,\"reasonType\":\"정산_계좌_미등록\","
				+ "\"reason\":\"계좌를 등록해 주세요\",\"status\":\"지급보류\",\"heldAt\":\"2026-11-03T09:00:00\","
				+ "\"releasedAt\":null}]"));

		assertThat(adapter.payoutSummary(3L))
			.isEqualTo(new OpsPort.PayoutSummary(LocalDate.of(2026, 11, 20), 1250000, 1));
		assertThat(adapter.holds(3L, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 11))).containsExactly(
			new OpsPort.Hold("한강 나이트런", 120000, "정산_계좌_미등록", "계좌를 등록해 주세요",
				LocalDateTime.of(2026, 11, 3, 9, 0), null));
		server.verify();
	}

	@Test
	@DisplayName("문의: 답변 대기 건수 (전체, 관리자 담당)")
	void inquiries() {
		server.expect(requestTo("http://svc/api/v1/inquiry/admin/inquiries?status=WAITING&page=0&size=1"))
			.andExpect(header("X-Member-Id", "20"))
			.andRespond(ok("{\"inquiries\":[{\"id\":1,\"nickname\":\"러너\"}],\"waitingCount\":5,\"answeredCount\":9}"));
		server.expect(
				requestTo("http://svc/api/v1/inquiry/admin/inquiries?status=WAITING&page=0&size=1&assignee=ADMIN"))
			.andRespond(ok("{\"inquiries\":[],\"waitingCount\":2,\"answeredCount\":1}"));

		assertThat(adapter.waitingInquiryCount(20L, false)).isEqualTo(5);
		assertThat(adapter.waitingInquiryCount(20L, true)).isEqualTo(2);
		server.verify();
	}

	@Test
	@DisplayName("회원: 승인 대기 판매자는 신청 시각만 읽는다")
	void pendingSellers() {
		server.expect(requestTo("http://svc/api/v1/member/admin/sellers?status=PENDING"))
			.andExpect(header("X-Member-Id", "20"))
			.andRespond(ok("[{\"id\":2,\"memberId\":8,\"businessName\":\"런컴퍼니\",\"representativeName\":\"박지민\","
				+ "\"status\":\"PENDING\",\"requestedAt\":\"2026-10-03T09:00:00\"}]"));

		assertThat(adapter.pendingSellerRequestedAts(20L)).containsExactly(LocalDateTime.of(2026, 10, 3, 9, 0));
		server.verify();
	}

	@Test
	@DisplayName("숫자 칸이 빠진 응답은 0으로 읽지 않고 예외다 (상대가 칸 이름을 바꿨을 때 「0건」이라고 답하지 않게)")
	void missingNumber() {
		server.expect(requestTo("http://svc/api/v1/order/seller/kits/summary"))
			.andRespond(ok("{\"preparingCount\":7,\"beforeRefundDeadline\":3}"));
		server.expect(requestTo("http://svc/api/v1/inquiry/admin/inquiries?status=WAITING&page=0&size=1"))
			.andRespond(ok("{\"inquiries\":[]}"));

		assertThatThrownBy(() -> adapter.kitSummary(3L)).isInstanceOf(RestClientException.class);
		assertThatThrownBy(() -> adapter.waitingInquiryCount(20L, false)).isInstanceOf(RestClientException.class);
	}

	@Test
	@DisplayName("역할이 없어 403이거나 값이 없는 응답이면 예외다 (도구가 실패로 다룬다)")
	void failures() {
		server.expect(requestTo("http://svc/api/v1/payout/seller/summary"))
			.andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
				.body("{\"status\":403,\"message\":\"판매자가 아닙니다.\",\"data\":null}"));
		server.expect(requestTo("http://svc/api/v1/payout/seller/summary"))
			.andRespond(withSuccess("{\"status\":200,\"message\":\"ok\",\"data\":null}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.payoutSummary(3L)).isInstanceOf(RestClientException.class);
		assertThatThrownBy(() -> adapter.payoutSummary(3L)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("문의 상세: 화면 종류대로 판매자용·관리자용 상세를 부르고, 회원 닉네임은 읽지 않는다")
	void inquiry() {
		server.expect(requestTo("http://svc/api/v1/inquiry/admin/inquiries/12"))
			.andExpect(header("X-Member-Id", "20"))
			.andRespond(ok("{\"id\":12,\"type\":\"APPLY_PAYMENT\",\"assignee\":\"ADMIN\",\"title\":\"환불\","
				+ "\"body\":\"언제 되나요\",\"status\":\"WAITING\",\"createDate\":\"2026-11-03T09:12:00\","
				+ "\"memberNickname\":\"한강러너\",\"order\":{\"orderNo\":\"OD-1\",\"paymentAmount\":45000,"
				+ "\"status\":\"PAID\",\"items\":[{\"raceId\":1,\"raceName\":\"춘천\",\"courseName\":\"10K\","
				+ "\"kitSummary\":null,\"deliveryStatus\":null}]}}"));
		server.expect(requestTo("http://svc/api/v1/inquiry/seller/inquiries/13"))
			.andExpect(header("X-Member-Id", "3"))
			.andRespond(ok("{\"type\":\"RACE_VENUE\",\"title\":\"주차\",\"body\":\"어디\",\"status\":\"WAITING\","
				+ "\"order\":null}"));

		assertThat(adapter.inquiry(20L, AiScreen.ADMIN, 12)).contains(new OpsPort.Inquiry("APPLY_PAYMENT", "환불",
			"언제 되나요", "WAITING", LocalDateTime.of(2026, 11, 3, 9, 12), new OpsPort.InquiryOrder("OD-1", 45000L,
			"PAID", List.of(new OpsPort.InquiryOrderItem("춘천", "10K", null, null)))));
		assertThat(adapter.inquiry(3L, AiScreen.SELLER, 13)).get()
			.extracting(OpsPort.Inquiry::order).isNull();
		server.verify();
	}

	@Test
	@DisplayName("문의 없음(INQUIRY_NOT_FOUND)만 빈 값이고, 다른 404는 계약이 어긋난 것이라 예외다")
	void inquiryNotFound() {
		server.expect(requestTo("http://svc/api/v1/inquiry/seller/inquiries/12"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
				.body("{\"status\":404,\"code\":\"INQUIRY_NOT_FOUND\",\"message\":\"문의를 찾을 수 없습니다.\"}"));
		server.expect(requestTo("http://svc/api/v1/inquiry/seller/inquiries/12"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
				.body("{\"status\":404,\"message\":\"No static resource\"}"));

		assertThat(adapter.inquiry(3L, AiScreen.SELLER, 12)).isEmpty();
		assertThatThrownBy(() -> adapter.inquiry(3L, AiScreen.SELLER, 12)).isInstanceOf(RestClientException.class);
	}

	private static ResponseCreator ok(String data) {
		return withSuccess("{\"status\":200,\"message\":\"ok\",\"data\":" + data + "}", MediaType.APPLICATION_JSON);
	}
}
