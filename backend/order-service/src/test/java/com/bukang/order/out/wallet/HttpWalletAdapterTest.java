package com.bukang.order.out.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.order.domain.exception.ExternalServiceRejectedException;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.domain.exception.OrderNotPayableException;
import com.bukang.order.domain.exception.PaymentBusyException;
import com.bukang.order.out.InternalHttpClients;
import com.sun.net.httpserver.HttpServer;

class HttpWalletAdapterTest {
	private static final String BASE_URL = "http://cash";
	private static final long ORDER_ID = 101;
	private static final long MEMBER_ID = 7;
	private static final String PAY_URL = BASE_URL + "/internal/v1/cash/payments/101";
	private static final String CANCEL_URL = PAY_URL + "/cancel";
	private static final String REFUND_URL = PAY_URL + "/refund";

	private MockRestServiceServer server;
	private HttpWalletAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpWalletAdapter(builder.build());
	}

	@Test
	@DisplayName("결제: 주문 ID를 경로에, 회원 ID·금액을 본문에 담아 보내고 결제 결과와 부족 금액을 옮긴다")
	void pay() {
		server.expect(requestTo(PAY_URL)).andExpect(method(HttpMethod.POST))
			.andExpect(content().json("{ \"memberId\": 7, \"amount\": 50000 }"))
			.andRespond(withSuccess(payResponse("PAID", 0), MediaType.APPLICATION_JSON));
		server.expect(requestTo(PAY_URL))
			.andRespond(withSuccess(payResponse("INSUFFICIENT_BALANCE", 20000), MediaType.APPLICATION_JSON));

		assertThat(adapter.pay(ORDER_ID, MEMBER_ID, 50_000)).isEqualTo(WalletPayResult.paid());
		assertThat(adapter.pay(ORDER_ID, MEMBER_ID, 50_000)).isEqualTo(WalletPayResult.insufficient(20_000));
		server.verify();
	}

	@Test
	@DisplayName("결제: D가 409로 거절하면(이미 취소·환불된 결제 등) 다시 해도 안 되므로 D의 이유를 그대로 결제 불가(409)로 알린다")
	void payConflict() {
		server.expect(requestTo(PAY_URL)).andRespond(withStatus(HttpStatus.CONFLICT)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{ \"status\": 409, \"message\": \"이미 취소·환불된 결제입니다.\", \"data\": null }"));
		server.expect(requestTo(PAY_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

		assertThatThrownBy(() -> adapter.pay(ORDER_ID, MEMBER_ID, 50_000))
			.isInstanceOf(OrderNotPayableException.class)
			.hasMessage("이미 취소·환불된 결제입니다.");
		// 본문이 없으면 기본 문구
		assertThatThrownBy(() -> adapter.pay(ORDER_ID, MEMBER_ID, 50_000))
			.isInstanceOf(OrderNotPayableException.class)
			.hasMessage(HttpWalletAdapter.PAYMENT_FINALIZED_MESSAGE);
		server.verify();
	}

	@Test
	@DisplayName("결제: 5xx·응답 지연·결과 없는 응답은 D가 처리했는지 모르므로 503 \"결과를 확인하지 못함\"으로 알린다")
	void payUnknown() {
		server.expect(requestTo(PAY_URL)).andRespond(withServerError());
		server.expect(requestTo(PAY_URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
		server.expect(requestTo(PAY_URL)).andRespond(withSuccess(
			"{ \"status\": 200, \"message\": \"결제\", \"data\": null }", MediaType.APPLICATION_JSON));
		server.expect(requestTo(PAY_URL)).andRespond(withSuccess(
			"{ \"status\": 200, \"message\": \"결제\", \"data\": {} }", MediaType.APPLICATION_JSON));
		// 모르는 결과 값, 잔액 부족인데 부족 금액이 없는 응답도 결과를 모르는 것으로 본다
		server.expect(requestTo(PAY_URL)).andRespond(withSuccess(
			"{ \"status\": 200, \"message\": \"결제\", \"data\": { \"outcome\": \"REFUNDED\" } }",
			MediaType.APPLICATION_JSON));
		server.expect(requestTo(PAY_URL)).andRespond(withSuccess(
			"{ \"status\": 200, \"message\": \"결제\", \"data\": { \"outcome\": \"INSUFFICIENT_BALANCE\" } }",
			MediaType.APPLICATION_JSON));

		for (int attempt = 0; attempt < 6; attempt++) {
			assertThatThrownBy(() -> adapter.pay(ORDER_ID, MEMBER_ID, 50_000))
				.isInstanceOf(ExternalServiceUnavailableException.class)
				.hasMessage(HttpWalletAdapter.PAY_UNKNOWN_MESSAGE);
		}
		server.verify();
	}

	@Test
	@DisplayName("결제: 409가 아닌 4xx(지갑 없음·요청 형식)는 D가 차감하지 않은 게 확실하므로 \"연결하지 못함\" 503으로 알린다")
	void payClientError() {
		server.expect(requestTo(PAY_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo(PAY_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

		for (int attempt = 0; attempt < 2; attempt++) {
			assertThatThrownBy(() -> adapter.pay(ORDER_ID, MEMBER_ID, 50_000))
				.isInstanceOf(ExternalServiceRejectedException.class)
				.hasMessage(HttpWalletAdapter.UNAVAILABLE_MESSAGE);
		}
		server.verify();
	}

	@Test
	@DisplayName("결제 취소·환불: 주문 ID를 경로에, 회원 ID를 본문에 담아 보낸다")
	void cancelAndRefund() {
		server.expect(requestTo(CANCEL_URL)).andExpect(method(HttpMethod.POST))
			.andExpect(content().json("{ \"memberId\": 7 }"))
			.andRespond(withSuccess(
				"{ \"status\": 200, \"message\": \"결제를 취소했습니다.\", \"data\": null }", MediaType.APPLICATION_JSON));
		server.expect(requestTo(REFUND_URL)).andExpect(method(HttpMethod.POST))
			.andExpect(content().json("{ \"memberId\": 7 }"))
			.andRespond(withSuccess(
				"{ \"status\": 200, \"message\": \"환불했습니다.\", \"data\": { \"amount\": 50000 } }",
				MediaType.APPLICATION_JSON));

		adapter.cancelPayment(ORDER_ID, MEMBER_ID);
		adapter.refund(ORDER_ID, MEMBER_ID);
		server.verify();
	}

	@Test
	@DisplayName("결제 취소·환불이 실패하면 503 예외를 던진다. 5xx·지연은 장애, 4xx 거절은 다시 해도 같은 거절로 나눈다")
	void cancelAndRefundFail() {
		server.expect(requestTo(CANCEL_URL)).andRespond(withServerError());
		server.expect(requestTo(CANCEL_URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
		server.expect(requestTo(REFUND_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.cancelPayment(ORDER_ID, MEMBER_ID))
			.isInstanceOf(ExternalServiceUnavailableException.class);
		assertThatThrownBy(() -> adapter.cancelPayment(ORDER_ID, MEMBER_ID))
			.isInstanceOf(ExternalServiceUnavailableException.class);
		assertThatThrownBy(() -> adapter.refund(ORDER_ID, MEMBER_ID))
			.isInstanceOf(ExternalServiceRejectedException.class)
			.hasMessage(HttpWalletAdapter.UNAVAILABLE_MESSAGE)
			// 회원·프론트에는 장애와 같은 오류 코드로 보인다
			.hasFieldOrPropertyWithValue("code", ExternalServiceUnavailableException.CODE);
		server.verify();
	}

	@Test
	@DisplayName("예치금 서비스가 시간 제한 안에 답하지 않으면 기다리지 않고 503 예외로 알린다 (실제 연결로 확인)")
	void timeout() throws Exception {
		// 연결은 받지만 아무 응답도 하지 않는 서버
		try (ServerSocket silentServer = new ServerSocket(0)) {
			HttpWalletAdapter slowAdapter = new HttpWalletAdapter(
				InternalHttpClients.create("http://localhost:" + silentServer.getLocalPort(), Duration.ofMillis(300)));
			long startedAt = System.nanoTime();

			assertThatThrownBy(() -> slowAdapter.pay(ORDER_ID, MEMBER_ID, 50_000))
				.isInstanceOf(ExternalServiceUnavailableException.class)
				.hasMessage(HttpWalletAdapter.PAY_UNKNOWN_MESSAGE);
			assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
		}
	}

	@Test
	@DisplayName("결제 동시 호출 제한: 5개가 함께 오면 4개만 예치금에 닿고, 1개는 1초 기다린 뒤 예치금을 부르지 않고 503으로 거절한다")
	void payConcurrencyLimit() throws Exception {
		AtomicInteger arrived = new AtomicInteger();
		CountDownLatch fourArrived = new CountDownLatch(4);
		CountDownLatch release = new CountDownLatch(1);
		HttpServer cash = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		cash.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		// 들어온 결제를 붙잡아 자리 4개를 다 쥐게 한다
		cash.createContext("/", exchange -> {
			arrived.incrementAndGet();
			fourArrived.countDown();
			awaitQuietly(release);
			byte[] body = payResponse("PAID", 0).getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		cash.start();
		HttpWalletAdapter limited = new HttpWalletAdapter(
			InternalHttpClients.create("http://localhost:" + cash.getAddress().getPort(), Duration.ofSeconds(10)),
			4, Duration.ofSeconds(1));
		try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<WalletPayResult>> calls = new ArrayList<>();
			long submittedAt = System.nanoTime();
			for (int i = 0; i < 5; i++) {
				long orderId = ORDER_ID + i;
				calls.add(callers.submit(() -> limited.pay(orderId, MEMBER_ID, 50_000)));
			}
			assertThat(fourArrived.await(5, TimeUnit.SECONDS)).isTrue();

			// 나머지 하나는 1초 기다린 뒤 거절된다. 그동안 예치금에는 4개만 닿아 있다
			long startedAt = System.nanoTime();
			Future<WalletPayResult> rejected = null;
			while (rejected == null) {
				rejected = calls.stream().filter(Future::isDone).findFirst().orElse(null);
				Thread.sleep(20);
				assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(3));
			}
			assertThat(Duration.ofNanos(System.nanoTime() - submittedAt))
				.isGreaterThanOrEqualTo(Duration.ofMillis(900));
			assertThatThrownBy(rejected::get).isInstanceOf(ExecutionException.class)
				.cause().isInstanceOf(PaymentBusyException.class);
			assertThat(arrived).hasValue(4);

			release.countDown();
			calls.remove(rejected);
			for (Future<WalletPayResult> call : calls) {
				assertThat(call.get(5, TimeUnit.SECONDS)).isEqualTo(WalletPayResult.paid());
			}
			assertThat(arrived).hasValue(4);
		} finally {
			release.countDown();
			cash.stop(0);
		}
	}

	@Test
	@DisplayName("결제 동시 호출 제한: 예치금이 5xx·409·잘못된 응답·연결 실패로 끝나도 자리를 돌려준다 (자리 1개, 기다림 없음)")
	void payPermitReleasedOnFailure() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer limitedServer = MockRestServiceServer.bindTo(builder).build();
		HttpWalletAdapter limited = new HttpWalletAdapter(builder.build(), 1, Duration.ZERO);
		limitedServer.expect(requestTo(PAY_URL)).andRespond(withServerError());
		limitedServer.expect(requestTo(PAY_URL)).andRespond(withStatus(HttpStatus.CONFLICT));
		limitedServer.expect(requestTo(PAY_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		limitedServer.expect(requestTo(PAY_URL)).andRespond(withException(new SocketTimeoutException()));
		limitedServer.expect(requestTo(PAY_URL))
			.andRespond(withSuccess(payResponse("PAID", 0), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> limited.pay(ORDER_ID, MEMBER_ID, 50_000))
			.isInstanceOf(ExternalServiceUnavailableException.class);
		assertThatThrownBy(() -> limited.pay(ORDER_ID, MEMBER_ID, 50_000))
			.isInstanceOf(OrderNotPayableException.class);
		assertThatThrownBy(() -> limited.pay(ORDER_ID, MEMBER_ID, 50_000))
			.isInstanceOf(ExternalServiceUnavailableException.class);
		assertThatThrownBy(() -> limited.pay(ORDER_ID, MEMBER_ID, 50_000))
			.isInstanceOf(ExternalServiceUnavailableException.class);
		// 자리가 새었다면 여기서 PaymentBusyException이 난다
		assertThat(limited.pay(ORDER_ID, MEMBER_ID, 50_000)).isEqualTo(WalletPayResult.paid());
		limitedServer.verify();
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	@Test
	@DisplayName("오류 본문은 로그에 앞부분만 남긴다 (D의 dev 오류 응답은 스택 트레이스를 통째로 담는다)")
	void abbreviateBody() {
		String longBody = "x".repeat(HttpWalletAdapter.LOGGED_BODY_LIMIT + 50);

		assertThat(HttpWalletAdapter.abbreviate("짧은 본문")).isEqualTo("짧은 본문");
		assertThat(HttpWalletAdapter.abbreviate(null)).isNull();
		assertThat(HttpWalletAdapter.abbreviate(longBody))
			.startsWith("x".repeat(HttpWalletAdapter.LOGGED_BODY_LIMIT))
			.endsWith("…(" + longBody.length() + "자)");
	}

	// 예치금 서비스 InternalV1CashController가 실제로 돌려주는 모양 (data = CashPaymentDto)
	private String payResponse(String outcome, long shortage) {
		return """
			{ "status": 200, "message": "결제했습니다.", "data": { "outcome": "%s", "shortage": %d } }
			""".formatted(outcome, shortage);
	}
}
