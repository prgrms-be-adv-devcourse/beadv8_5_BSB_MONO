package com.bukang.cash.out.pg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.cash.domain.exception.PaymentApprovalFailedException;
import com.bukang.cash.domain.exception.PgLookupFailedException;

class TossPaymentClientTest {
	private static final String BASE_URL = "http://toss";
	private static final String SECRET_KEY = "test_sk_dummy";
	private static final String URL = BASE_URL + TossPaymentClient.CONFIRM_PATH;
	private static final String LOOKUP_URL = BASE_URL + "/v1/payments/orders/order_1";
	private static final String BASIC_AUTH =
		"Basic " + Base64.getEncoder().encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));

	private MockRestServiceServer server;
	private TossPaymentClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.build();
		client = new TossPaymentClient(restClient, restClient, SECRET_KEY);
	}

	@Test
	@DisplayName("시크릿 키로 Basic 인증 헤더를 넣고, paymentKey·orderId·amount를 요청 바디에 담아 보낸다")
	void sendsAuthHeaderAndBody() {
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.paymentKey").value("pay_123"))
			.andExpect(jsonPath("$.orderId").value("order_1"))
			.andExpect(jsonPath("$.amount").value(10_000))
			.andRespond(withSuccess(confirmResponse("pay_123", 10_000), MediaType.APPLICATION_JSON));

		client.approve("pay_123", "order_1", 10_000);

		server.verify();
	}

	@Test
	@DisplayName("승인 성공 시 응답의 승인 금액·결제 키를 돌려준다")
	void approveSuccess() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess(confirmResponse("pay_123", 10_000), MediaType.APPLICATION_JSON));

		PaymentApprovalResult result = client.approve("pay_123", "order_1", 10_000);

		assertThat(result.approvedAmount()).isEqualTo(10_000);
		assertThat(result.pgPaymentKey()).isEqualTo("pay_123");
	}

	@Test
	@DisplayName("응답에 우리가 안 쓰는 필드가 더 있어도 무시하고 파싱한다")
	void ignoresUnknownResponseFields() {
		String responseWithExtraFields = """
			{ "paymentKey": "pay_123", "totalAmount": 10000, "method": "카드", "mId": "tosspayments",
				"approvedAt": "2026-10-15T10:00:00+09:00" }
			""";
		server.expect(requestTo(URL)).andRespond(withSuccess(responseWithExtraFields, MediaType.APPLICATION_JSON));

		PaymentApprovalResult result = client.approve("pay_123", "order_1", 10_000);

		assertThat(result.approvedAmount()).isEqualTo(10_000);
		assertThat(result.pgPaymentKey()).isEqualTo("pay_123");
	}

	@Test
	@DisplayName("Toss가 4xx로 거절하면(예: 존재하지 않는 결제) 결제 승인 실패 예외로 알린다")
	void rejectedWithClientError() {
		server.expect(requestTo(URL))
			.andRespond(withStatus(HttpStatus.NOT_FOUND)
				.body(errorResponse("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."))
				.contentType(MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.approve("pay_123", "order_1", 10_000))
			.isInstanceOf(PaymentApprovalFailedException.class);
	}

	@Test
	@DisplayName("Toss가 4xx로 거절하면 응답 본문의 code를 예외에 담아 둔다 (PRO-200)")
	void keepsRejectionCodeOnException() {
		server.expect(requestTo(URL))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.body(errorResponse("ALREADY_PROCESSED_PAYMENT", "이미 처리가 끝난 결제 입니다."))
				.contentType(MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.approve("pay_123", "order_1", 10_000))
			.asInstanceOf(InstanceOfAssertFactories.type(PaymentApprovalFailedException.class))
			.extracting(PaymentApprovalFailedException::getPgCode)
			.isEqualTo("ALREADY_PROCESSED_PAYMENT");
	}

	@Test
	@DisplayName("사용자 사유 거절(카드 한도 초과 등)은 결과가 분명하고, Toss 문구를 그대로 사용자 문구로 담는다")
	void userReasonRejectionKeepsTossMessage() {
		server.expect(requestTo(URL))
			.andRespond(withStatus(HttpStatus.FORBIDDEN)
				.body(errorResponse("REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족으로 결제에 실패했습니다."))
				.contentType(MediaType.APPLICATION_JSON));

		PaymentApprovalFailedException failure = approveFailure();

		assertThat(failure.isResultUnknown()).isFalse();
		assertThat(failure.getMessage()).isEqualTo("한도초과 혹은 잔액부족으로 결제에 실패했습니다.");
	}

	@Test
	@DisplayName("우리 쪽 키·연동 설정 문제로 거절되면 Toss 문구 대신 고정 문구를 담는다")
	void configRejectionHidesTossMessage() {
		server.expect(requestTo(URL))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.body(errorResponse("INVALID_API_KEY", "잘못된 시크릿키 연동 정보 입니다."))
				.contentType(MediaType.APPLICATION_JSON));

		PaymentApprovalFailedException failure = approveFailure();

		assertThat(failure.isResultUnknown()).isFalse();
		assertThat(failure.getMessage()).isEqualTo(TossPaymentClient.GENERIC_REJECTION_MESSAGE);
	}

	@Test
	@DisplayName("이미 처리됨·처리 중 응답은 승인됐는지 알 수 없다고 알린다")
	void alreadyProcessedOrProcessingIsResultUnknown() {
		server.expect(requestTo(URL))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.body(errorResponse("ALREADY_PROCESSING_REQUEST", "이미 처리중인 요청입니다."))
				.contentType(MediaType.APPLICATION_JSON));

		assertThat(approveFailure().isResultUnknown()).isTrue();
	}

	@Test
	@DisplayName("Toss가 5xx로 답하면 승인됐는지 알 수 없다고 알린다")
	void serverError() {
		server.expect(requestTo(URL)).andRespond(withServerError());

		assertThat(approveFailure().isResultUnknown()).isTrue();
	}

	@Test
	@DisplayName("Toss 서버에 연결하지 못하면(타임아웃 등) 결제 승인 실패 예외로 알린다")
	void connectionFailure() {
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

		assertThatThrownBy(() -> client.approve("pay_123", "order_1", 10_000))
			.isInstanceOf(PaymentApprovalFailedException.class);
	}

	@Test
	@DisplayName("응답이 비어 있으면 결제 승인 실패 예외로 알린다")
	void emptyResponseBody() {
		server.expect(requestTo(URL)).andRespond(withSuccess());

		assertThatThrownBy(() -> client.approve("pay_123", "order_1", 10_000))
			.isInstanceOf(PaymentApprovalFailedException.class);
	}

	@Test
	@DisplayName("조회 결과가 DONE이면 승인 완료와 승인 결과를 돌려준다")
	void lookupReturnsCompletedWhenDone() {
		server.expect(requestTo(LOOKUP_URL))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
			.andRespond(withSuccess(paymentResponse("pay_123", 10_000, "DONE"), MediaType.APPLICATION_JSON));

		PaymentLookupResult result = client.lookupByOrderId("order_1");

		assertThat(result.status()).isEqualTo(PaymentLookupResult.Status.COMPLETED);
		assertThat(result.approval().approvedAmount()).isEqualTo(10_000);
		assertThat(result.approval().pgPaymentKey()).isEqualTo("pay_123");
	}

	@Test
	@DisplayName("조회 결과가 승인 실패·만료·전액 취소면 승인 안 됨으로 확정한다")
	void lookupReturnsNotCompletedWhenAbortedOrCanceled() {
		server.expect(requestTo(LOOKUP_URL))
			.andRespond(withSuccess(paymentResponse("pay_123", 10_000, "CANCELED"), MediaType.APPLICATION_JSON));

		assertThat(client.lookupByOrderId("order_1").status()).isEqualTo(PaymentLookupResult.Status.NOT_COMPLETED);
	}

	@Test
	@DisplayName("조회 결과가 IN_PROGRESS(인증만 끝났거나 승인 처리 중)면 승인 안 됨으로 단정하지 않고 아직 정해지지 않았다고 돌려준다")
	void lookupReturnsPendingWhenInProgress() {
		server.expect(requestTo(LOOKUP_URL))
			.andRespond(withSuccess(paymentResponse("pay_123", 10_000, "IN_PROGRESS"), MediaType.APPLICATION_JSON));

		assertThat(client.lookupByOrderId("order_1").status()).isEqualTo(PaymentLookupResult.Status.PENDING);
	}

	@Test
	@DisplayName("Toss가 이 주문번호의 결제를 모르면(404) 승인 안 됨으로 확정한다")
	void lookupReturnsNotCompletedWhenPaymentNotFound() {
		server.expect(requestTo(LOOKUP_URL))
			.andRespond(withStatus(HttpStatus.NOT_FOUND)
				.body(errorResponse("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."))
				.contentType(MediaType.APPLICATION_JSON));

		assertThat(client.lookupByOrderId("order_1").status()).isEqualTo(PaymentLookupResult.Status.NOT_COMPLETED);
	}

	@Test
	@DisplayName("조회 자체가 실패하면(타임아웃 등) 승인 여부를 확인할 수 없다는 뜻이라 PgLookupFailedException으로 알린다")
	void lookupThrowsWhenLookupFails() {
		server.expect(requestTo(LOOKUP_URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

		assertThatThrownBy(() -> client.lookupByOrderId("order_1"))
			.isInstanceOf(PgLookupFailedException.class);
	}

	private PaymentApprovalFailedException approveFailure() {
		return catchThrowableOfType(PaymentApprovalFailedException.class,
			() -> client.approve("pay_123", "order_1", 10_000));
	}

	// Toss 결제 승인·조회 응답의 모양 (우리가 실제로 쓰는 필드만)
	private String confirmResponse(String paymentKey, long totalAmount) {
		return """
			{ "paymentKey": "%s", "totalAmount": %d }
			""".formatted(paymentKey, totalAmount);
	}

	private String paymentResponse(String paymentKey, long totalAmount, String status) {
		return """
			{ "paymentKey": "%s", "totalAmount": %d, "status": "%s" }
			""".formatted(paymentKey, totalAmount, status);
	}

	// Toss 오류 응답의 모양
	private String errorResponse(String code, String message) {
		return """
			{ "code": "%s", "message": "%s" }
			""".formatted(code, message);
	}
}
