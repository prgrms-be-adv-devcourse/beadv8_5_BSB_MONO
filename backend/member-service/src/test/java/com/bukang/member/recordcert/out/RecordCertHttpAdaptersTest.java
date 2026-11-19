package com.bukang.member.recordcert.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.member.recordcert.domain.CourseClass;
import com.bukang.member.recordcert.domain.CourseRule;
import com.bukang.member.recordcert.domain.exception.RecordCertCheckUnavailableException;
import com.bukang.member.recordcert.out.CourseRulePort.SellerCourseRule;
import com.bukang.member.recordcert.out.TicketPort.Ticket;

// 기록증 제출의 세 HTTP 어댑터 (BUK-274). 404는 "없음", 그 밖의 실패는 503으로 알리는지 본다
class RecordCertHttpAdaptersTest {
	private static final String BASE_URL = "http://service";
	private static final String TICKET_URL = BASE_URL + "/internal/v1/order/order-items/42/ticket";
	private static final String UPLOADER_URL = BASE_URL + "/internal/v1/file/files/7/uploader";
	private static final String RULE_URL = BASE_URL + "/internal/v1/product/courses/12/record-cert-rule";

	private MockRestServiceServer server;
	private HttpTicketAdapter ticketAdapter;
	private HttpFileUploaderAdapter fileUploaderAdapter;
	private HttpCourseRuleAdapter courseRuleAdapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.build();
		ticketAdapter = new HttpTicketAdapter(restClient);
		fileUploaderAdapter = new HttpFileUploaderAdapter(restClient);
		courseRuleAdapter = new HttpCourseRuleAdapter(restClient);
	}

	@Test
	@DisplayName("참가권 · 올린 사람은 응답 값을, 404면 빈 값을 돌려준다")
	void ticketAndUploader() {
		server.expect(requestTo(TICKET_URL)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("""
			{ "status": 200, "data": { "buyerId": 3, "raceId": 5, "raceCourseId": 10, "status": "PAID" } }
			""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(TICKET_URL)).andRespond(withResourceNotFound());
		server.expect(requestTo(UPLOADER_URL)).andRespond(withSuccess("""
			{ "status": 200, "data": { "memberId": 3, "fileType": "RECORD_CERT", "status": "UPLOADED" } }
			""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(UPLOADER_URL)).andRespond(withResourceNotFound());
		// 다른 용도로 올렸거나 이미 연결된 사진은 받지 않는다 (리뷰 #643)
		server.expect(requestTo(UPLOADER_URL)).andRespond(withSuccess("""
			{ "status": 200, "data": { "memberId": 3, "fileType": "THUMBNAIL", "status": "UPLOADED" } }
			""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(UPLOADER_URL)).andRespond(withSuccess("""
			{ "status": 200, "data": { "memberId": 3, "fileType": "RECORD_CERT", "status": "ACTIVE" } }
			""", MediaType.APPLICATION_JSON));

		assertThat(ticketAdapter.find(42)).contains(new Ticket(3, 5, 10, "PAID"));
		assertThat(ticketAdapter.find(42)).isEmpty();
		assertThat(fileUploaderAdapter.findUploader(7)).contains(3L);
		assertThat(fileUploaderAdapter.findUploader(7)).isEmpty();
		assertThat(fileUploaderAdapter.findUploader(7)).isEmpty();
		assertThat(fileUploaderAdapter.findUploader(7)).isEmpty();
		server.verify();
	}

	@Test
	@DisplayName("종목 규칙은 product 종목 구분을 member 종목 구분으로 바꾸고, 규칙이 없으면 rule이 null이다")
	void courseRule() {
		server.expect(requestTo(RULE_URL)).andRespond(withSuccess("""
			{ "status": 200, "data": { "sellerMemberId": 9, "rule": {
				"recognizedSince": "2025-01-01", "maxNetSeconds": 7200,
				"allowedCourseTypes": ["KM_5", "KM_10", "HALF"] } } }
			""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(RULE_URL)).andRespond(withSuccess("""
			{ "status": 200, "data": { "sellerMemberId": 9, "rule": null } }
			""", MediaType.APPLICATION_JSON));

		assertThat(courseRuleAdapter.find(12)).isEqualTo(new SellerCourseRule(9, new CourseRule(
			LocalDate.of(2025, 1, 1), 7200, Set.of(CourseClass.FIVE_K, CourseClass.TEN_K, CourseClass.HALF))));
		assertThat(courseRuleAdapter.find(12)).isEqualTo(new SellerCourseRule(9, null));
	}

	@Test
	@DisplayName("5xx · 시간 초과 · 값 없는 응답은 모르는 것이라 503 예외로 알린다")
	void unavailable() {
		server.expect(requestTo(TICKET_URL)).andRespond(withServerError());
		server.expect(requestTo(TICKET_URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
		server.expect(requestTo(TICKET_URL)).andRespond(withSuccess("""
			{ "status": 200, "data": { "buyerId": 3 } }
			""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(UPLOADER_URL)).andRespond(withServerError());
		server.expect(requestTo(UPLOADER_URL)).andRespond(withSuccess("""
			{ "status": 200, "data": {} }
			""", MediaType.APPLICATION_JSON));

		for (int attempt = 0; attempt < 3; attempt++) {
			assertThatThrownBy(() -> ticketAdapter.find(42)).isInstanceOf(RecordCertCheckUnavailableException.class);
		}
		for (int attempt = 0; attempt < 2; attempt++) {
			assertThatThrownBy(() -> fileUploaderAdapter.findUploader(7))
				.isInstanceOf(RecordCertCheckUnavailableException.class);
		}
		server.verify();
	}

	@Test
	@DisplayName("종목 규칙은 404 · 5xx · 모르는 종목 구분도 503이다 (참가권이 가리키는 종목이라 없으면 판단할 수 없다)")
	void courseRuleUnavailable() {
		server.expect(requestTo(RULE_URL)).andRespond(withResourceNotFound());
		server.expect(requestTo(RULE_URL)).andRespond(withServerError());
		server.expect(requestTo(RULE_URL)).andRespond(withSuccess("""
			{ "status": 200, "data": { "sellerMemberId": 9, "rule": {
				"recognizedSince": "2025-01-01", "maxNetSeconds": 7200, "allowedCourseTypes": ["KM_42"] } } }
			""", MediaType.APPLICATION_JSON));

		for (int attempt = 0; attempt < 3; attempt++) {
			assertThatThrownBy(() -> courseRuleAdapter.find(12))
				.isInstanceOf(RecordCertCheckUnavailableException.class);
		}
		server.verify();
	}
}
