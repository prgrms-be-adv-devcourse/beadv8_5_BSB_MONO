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
import java.time.LocalDateTime;
import java.util.Base64;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.cash.domain.exception.charge.PaymentApprovalFailedException;
import com.bukang.cash.domain.exception.charge.PaymentLookupFailedException;
import com.bukang.cash.domain.exception.recovery.VirtualAccountIssueFailedException;
import com.bukang.cash.domain.exception.withdrawal.PaymentCancelFailedException;

class TossPaymentClientTest {
	private static final String BASE_URL = "http://toss";
	private static final String SECRET_KEY = "test_sk_dummy";
	private static final String URL = BASE_URL + TossPaymentConfirmClient.CONFIRM_PATH;
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
	@DisplayName("승인 성공 시 응답의 승인 시각(approvedAt)을 한국 시간으로 돌려준다")
	void approveReturnsApprovedAt() {
		String response = """
			{ "paymentKey": "pay_123", "totalAmount": 10000, "approvedAt": "2026-10-15T10:00:00+09:00" }
			""";
		server.expect(requestTo(URL)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

		PaymentApprovalResult result = client.approve("pay_123", "order_1", 10_000);

		assertThat(result.approvedAt()).isEqualTo(LocalDateTime.of(2026, 10, 15, 10, 0));
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
		assertThat(failure.getMessage()).isEqualTo(TossPaymentConfirmClient.GENERIC_REJECTION_MESSAGE);
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
	@DisplayName("재확인 조회 결과가 DONE이면 승인 시각(approvedAt)도 한국 시간으로 돌려준다")
	void lookupReturnsApprovedAtWhenDone() {
		String response = """
			{ "paymentKey": "pay_123", "totalAmount": 10000, "status": "DONE",
				"approvedAt": "2026-10-15T10:00:00+09:00" }
			""";
		server.expect(requestTo(LOOKUP_URL)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

		PaymentLookupResult result = client.lookupByOrderId("order_1");

		assertThat(result.approval().approvedAt()).isEqualTo(LocalDateTime.of(2026, 10, 15, 10, 0));
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
	@DisplayName("조회 결과가 우리가 모르는 상태값이면(Toss가 새로 추가한 값 등) 실패로 단정하지 않고 아직 정해지지 않았다고 돌려준다")
	void lookupReturnsPendingWhenStatusIsUnknown() {
		server.expect(requestTo(LOOKUP_URL))
			.andRespond(withSuccess(paymentResponse("pay_123", 10_000, "SOMETHING_NEW"), MediaType.APPLICATION_JSON));

		assertThat(client.lookupByOrderId("order_1").status()).isEqualTo(PaymentLookupResult.Status.PENDING);
	}

	@ParameterizedTest(name = "{0} → 입금 대기 표시 {1}")
	@CsvSource({"WAITING_FOR_DEPOSIT, true", "PARTIAL_CANCELED, false", "SOMETHING_NEW, false"})
	@DisplayName("조회 결과가 가상계좌 입금 대기일 때만 입금 대기 표시를 붙이고, 일부 취소·모르는 값엔 붙이지 않는다 (셋 다 아직 정해지지 않음)")
	void lookupMarksAwaitingDepositOnlyWhenWaitingForDeposit(String tossStatus, boolean awaitingDeposit) {
		server.expect(requestTo(LOOKUP_URL))
			.andRespond(withSuccess(paymentResponse("pay_123", 10_000, tossStatus), MediaType.APPLICATION_JSON));

		PaymentLookupResult result = client.lookupByOrderId("order_1");

		assertThat(result.status()).isEqualTo(PaymentLookupResult.Status.PENDING);
		assertThat(result.awaitingDeposit()).isEqualTo(awaitingDeposit);
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
	@DisplayName("조회 자체가 실패하면(타임아웃 등) 승인 여부를 확인할 수 없다는 뜻이라 PaymentLookupFailedException으로 알린다")
	void lookupThrowsWhenLookupFails() {
		server.expect(requestTo(LOOKUP_URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

		assertThatThrownBy(() -> client.lookupByOrderId("order_1"))
			.isInstanceOf(PaymentLookupFailedException.class);
	}

	@Test
	@DisplayName("취소 사유와 취소 금액을 요청 바디에 담아 결제 키로 취소를 요청한다")
	void sendsCancelReasonInBody() {
		String url = BASE_URL + "/v1/payments/pay_123/cancel";
		server.expect(requestTo(url))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
			.andExpect(jsonPath("$.cancelReason").value("고객 요청 환급"))
			.andExpect(jsonPath("$.cancelAmount").value(10_000))
			.andRespond(withSuccess(cancelResponse(10_000, "txn_1"), MediaType.APPLICATION_JSON));

		client.cancel("pay_123", "고객 요청 환급", 10_000, "withdrawal-item-1");

		server.verify();
	}

	@Test
	@DisplayName("10,000원 충전의 PG 결제 중 남은 5,000원만 결제취소하면 cancelAmount 5,000을 보내고 취소 금액 5,000을 돌려준다")
	void partialCancelSendsCancelAmount() {
		String url = BASE_URL + "/v1/payments/pay_123/cancel";
		server.expect(requestTo(url))
			.andExpect(jsonPath("$.cancelAmount").value(5_000))
			.andRespond(withSuccess(cancelResponse(5_000, "txn_partial"), MediaType.APPLICATION_JSON));

		PaymentCancelResult result = client.cancel("pay_123", "고객 요청 환급", 5_000, "withdrawal-item-1");

		server.verify();
		assertThat(result.canceledAmount()).isEqualTo(5_000);
	}

	@Test
	@DisplayName("결제취소 요청 헤더에 몫별 중복 방지 키(Idempotency-Key)를 담아, 같은 키로 다시 보내도 토스가 한 번만 취소하게 한다")
	void cancelSendsIdempotencyKeyHeader() {
		String url = BASE_URL + "/v1/payments/pay_123/cancel";
		server.expect(requestTo(url))
			.andExpect(header("Idempotency-Key", "withdrawal-item-7"))
			.andRespond(withSuccess(cancelResponse(5_000, "txn_7"), MediaType.APPLICATION_JSON));

		client.cancel("pay_123", "고객 요청 환급", 5_000, "withdrawal-item-7");

		server.verify();
	}

	@Test
	@DisplayName("취소 성공 시 cancels 배열의 마지막 항목(이번 취소 건)의 금액·거래 키를 돌려준다")
	void cancelSuccess() {
		String url = BASE_URL + "/v1/payments/pay_123/cancel";
		server.expect(requestTo(url))
			.andRespond(withSuccess(cancelResponse(10_000, "txn_1"), MediaType.APPLICATION_JSON));

		PaymentCancelResult result = client.cancel("pay_123", "고객 요청 환급", 10_000, "withdrawal-item-1");

		assertThat(result.canceledAmount()).isEqualTo(10_000);
		assertThat(result.transactionKey()).isEqualTo("txn_1");
	}

	@Test
	@DisplayName("Toss가 4xx로 취소를 거절하면(예: 이미 취소된 결제) 결제 취소 실패 예외로 알리고 code를 담아 둔다")
	void cancelRejectedWithClientError() {
		String url = BASE_URL + "/v1/payments/pay_123/cancel";
		server.expect(requestTo(url))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.body(errorResponse("ALREADY_CANCELED_PAYMENT", "이미 취소된 결제 입니다."))
				.contentType(MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.cancel("pay_123", "고객 요청 환급", 10_000, "withdrawal-item-1"))
			.asInstanceOf(InstanceOfAssertFactories.type(PaymentCancelFailedException.class))
			.extracting(PaymentCancelFailedException::getPgCode)
			.isEqualTo("ALREADY_CANCELED_PAYMENT");
	}

	@Test
	@DisplayName("같은 키의 취소가 아직 처리 중이라 Toss가 409(IDEMPOTENT_REQUEST_PROCESSING)를 주면 실패가 아니라 결과 모름으로 알린다")
	void cancelIdempotentRequestProcessingIsResultUnknown() {
		String url = BASE_URL + "/v1/payments/pay_123/cancel";
		server.expect(requestTo(url))
			.andRespond(withStatus(HttpStatus.CONFLICT)
				.body(errorResponse("IDEMPOTENT_REQUEST_PROCESSING", "이전 멱등 요청이 처리중입니다."))
				.contentType(MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.cancel("pay_123", "고객 요청 환급", 10_000, "withdrawal-item-1"))
			.asInstanceOf(InstanceOfAssertFactories.type(PaymentCancelFailedException.class))
			.extracting(PaymentCancelFailedException::isResultUnknown)
			.isEqualTo(true);
	}

	@Test
	@DisplayName("Toss가 5xx로 답하면 취소를 처리했는지 알 수 없으므로 결과 모름으로 알린다")
	void cancelServerErrorIsResultUnknown() {
		String url = BASE_URL + "/v1/payments/pay_123/cancel";
		server.expect(requestTo(url)).andRespond(withServerError());

		assertThatThrownBy(() -> client.cancel("pay_123", "고객 요청 환급", 10_000, "withdrawal-item-1"))
			.asInstanceOf(InstanceOfAssertFactories.type(PaymentCancelFailedException.class))
			.extracting(PaymentCancelFailedException::isResultUnknown)
			.isEqualTo(true);
	}

	@Test
	@DisplayName("응답 전에 연결이 끊기면(타임아웃 등) Toss가 취소했을 수도 있으므로 결과 모름으로 알린다")
	void cancelConnectionFailureIsResultUnknown() {
		String url = BASE_URL + "/v1/payments/pay_123/cancel";
		server.expect(requestTo(url)).andRespond(withException(new SocketTimeoutException("read timed out")));

		assertThatThrownBy(() -> client.cancel("pay_123", "고객 요청 환급", 10_000, "withdrawal-item-1"))
			.asInstanceOf(InstanceOfAssertFactories.type(PaymentCancelFailedException.class))
			.extracting(PaymentCancelFailedException::isResultUnknown)
			.isEqualTo(true);
	}

	@Test
	@DisplayName("200인데 cancels 배열이 비어 있으면 Toss는 취소를 받아 준 것이라 실패가 아니라 결과 모름으로 알린다")
	void cancelResponseWithoutCancelsIsResultUnknown() {
		String url = BASE_URL + "/v1/payments/pay_123/cancel";
		server.expect(requestTo(url)).andRespond(withSuccess("{ \"cancels\": [] }", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.cancel("pay_123", "고객 요청 환급", 10_000, "withdrawal-item-1"))
			.asInstanceOf(InstanceOfAssertFactories.type(PaymentCancelFailedException.class))
			.extracting(PaymentCancelFailedException::isResultUnknown)
			.isEqualTo(true);
	}

	@Test
	@DisplayName("청구 ID를 주문번호로 국민은행·90일 유효 가상계좌 발급을 요청하고, 계좌번호·금액·입금 기한(한국 시간)·웹훅 비밀값을 돌려준다")
	void issueVirtualAccount() {
		String response = """
			{ "orderId": "RC-20261214-0A3F", "status": "WAITING_FOR_DEPOSIT", "secret": "ps_secret_1",
			"totalAmount": 400000,
			"virtualAccount": { "accountNumber": "X6000123456789", "bankCode": "06",
			"dueDate": "2027-03-14T10:00:00+09:00" } }
			""";
		server.expect(requestTo(BASE_URL + "/v1/virtual-accounts"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
			.andExpect(jsonPath("$.orderId").value("RC-20261214-0A3F"))
			.andExpect(jsonPath("$.amount").value(400_000))
			.andExpect(jsonPath("$.bank").value("06"))
			.andExpect(jsonPath("$.validHours").value(2160))
			.andExpect(jsonPath("$.orderName").isNotEmpty())
			.andExpect(jsonPath("$.customerName").isNotEmpty())
			.andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

		VirtualAccountIssueResult result = client.issueVirtualAccount("RC-20261214-0A3F", 400_000);

		server.verify();
		assertThat(result).isEqualTo(new VirtualAccountIssueResult(
			"국민은행", "X6000123456789", 400_000, LocalDateTime.of(2027, 3, 14, 10, 0), "ps_secret_1"));
	}

	@Test
	@DisplayName("Toss가 4xx로 발급을 거절하면 가상계좌 발급 실패 예외로 알린다")
	void issueVirtualAccountRejected() {
		server.expect(requestTo(BASE_URL + "/v1/virtual-accounts"))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON)
				.body(errorResponse("DUPLICATED_ORDER_ID", "이미 승인 및 취소가 진행된 중복된 주문번호 입니다.")));

		assertThatThrownBy(() -> client.issueVirtualAccount("RC-20261214-0A3F", 400_000))
			.isInstanceOf(VirtualAccountIssueFailedException.class);
	}

	@Test
	@DisplayName("Toss가 5xx로 답하면 가상계좌 발급 실패 예외로 알린다")
	void issueVirtualAccountServerError() {
		server.expect(requestTo(BASE_URL + "/v1/virtual-accounts")).andRespond(withServerError());

		assertThatThrownBy(() -> client.issueVirtualAccount("RC-20261214-0A3F", 400_000))
			.isInstanceOf(VirtualAccountIssueFailedException.class);
	}

	@Test
	@DisplayName("Toss 서버에 연결하지 못하면(타임아웃 등) 가상계좌 발급 실패 예외로 알린다")
	void issueVirtualAccountConnectionFailure() {
		server.expect(requestTo(BASE_URL + "/v1/virtual-accounts"))
			.andRespond(withException(new SocketTimeoutException("read timed out")));

		assertThatThrownBy(() -> client.issueVirtualAccount("RC-20261214-0A3F", 400_000))
			.isInstanceOf(VirtualAccountIssueFailedException.class);
	}

	@Test
	@DisplayName("200인데 계좌 정보가 비어 있으면 가상계좌 발급 실패 예외로 알린다")
	void issueVirtualAccountWithoutAccount() {
		server.expect(requestTo(BASE_URL + "/v1/virtual-accounts"))
			.andRespond(withSuccess("{ \"secret\": \"ps_secret_1\" }", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.issueVirtualAccount("RC-20261214-0A3F", 400_000))
			.isInstanceOf(VirtualAccountIssueFailedException.class);
	}

	@Test
	@DisplayName("주문번호로 이미 발급된 가상계좌가 있으면 계좌번호·금액·입금 기한(한국 시간)·웹훅 비밀값을 돌려준다")
	void findVirtualAccount() {
		String response = """
			{ "orderId": "RC-20261214-0A3F", "status": "WAITING_FOR_DEPOSIT", "secret": "ps_secret_1",
			"totalAmount": 400000,
			"virtualAccount": { "accountNumber": "X6000123456789", "bankCode": "06",
			"dueDate": "2027-03-14T10:00:00+09:00" } }
			""";
		server.expect(requestTo(BASE_URL + "/v1/payments/orders/RC-20261214-0A3F"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
			.andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

		assertThat(client.findVirtualAccount("RC-20261214-0A3F")).contains(new VirtualAccountIssueResult(
			"국민은행", "X6000123456789", 400_000, LocalDateTime.of(2027, 3, 14, 10, 0), "ps_secret_1"));
	}

	@Test
	@DisplayName("Toss가 이 주문번호의 결제를 모르면(404) 발급된 가상계좌가 없다고 돌려준다")
	void findVirtualAccountNotFound() {
		server.expect(requestTo(BASE_URL + "/v1/payments/orders/RC-20261214-0A3F"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND)
				.contentType(MediaType.APPLICATION_JSON)
				.body(errorResponse("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다.")));

		assertThat(client.findVirtualAccount("RC-20261214-0A3F")).isEmpty();
	}

	@Test
	@DisplayName("조회가 5xx로 실패하면 발급 여부를 알 수 없어 새로 발급하지 않도록 가상계좌 발급 실패 예외로 알린다")
	void findVirtualAccountServerError() {
		server.expect(requestTo(BASE_URL + "/v1/payments/orders/RC-20261214-0A3F")).andRespond(withServerError());

		assertThatThrownBy(() -> client.findVirtualAccount("RC-20261214-0A3F"))
			.isInstanceOf(VirtualAccountIssueFailedException.class);
	}

	@Test
	@DisplayName("조회 서버에 연결하지 못하면(타임아웃 등) 가상계좌 발급 실패 예외로 알린다")
	void findVirtualAccountConnectionFailure() {
		server.expect(requestTo(BASE_URL + "/v1/payments/orders/RC-20261214-0A3F"))
			.andRespond(withException(new SocketTimeoutException("read timed out")));

		assertThatThrownBy(() -> client.findVirtualAccount("RC-20261214-0A3F"))
			.isInstanceOf(VirtualAccountIssueFailedException.class);
	}

	@Test
	@DisplayName("주문번호의 결제는 있는데 가상계좌 정보가 없으면 새로 발급하지 않도록 가상계좌 발급 실패 예외로 알린다")
	void findVirtualAccountWithoutAccount() {
		server.expect(requestTo(BASE_URL + "/v1/payments/orders/RC-20261214-0A3F"))
			.andRespond(withSuccess("{ \"status\": \"DONE\" }", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.findVirtualAccount("RC-20261214-0A3F"))
			.isInstanceOf(VirtualAccountIssueFailedException.class);
	}

	private PaymentApprovalFailedException approveFailure() {
		return catchThrowableOfType(PaymentApprovalFailedException.class,
			() -> client.approve("pay_123", "order_1", 10_000));
	}

	// Toss 취소 응답의 모양 (우리가 실제로 쓰는 필드만, cancels 배열 마지막 항목이 이번 취소 건)
	private String cancelResponse(long cancelAmount, String transactionKey) {
		return """
			{ "cancels": [ { "cancelAmount": %d, "transactionKey": "%s" } ] }
			""".formatted(cancelAmount, transactionKey);
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
