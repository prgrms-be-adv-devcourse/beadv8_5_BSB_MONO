package com.bukang.gateway.error;

import java.net.ConnectException;
import java.net.UnknownHostException;

import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import reactor.core.publisher.Mono;

/**
 * 서비스가 늦거나 꺼져 있을 때 게이트웨이의 오류 응답을 RsData JSON으로 바꾼다.
 * 응답 시간 초과는 504, 서비스에 연결하지 못함(연결 거부·연결 시간 초과·이름을 찾지 못함)은 503으로 보낸다.
 * 라우트가 없는 주소(/internal/** 포함)는 404로 보낸다. 그 밖의 오류는 손대지 않고 Spring 기본 오류 처리기에 넘긴다.
 */
@Component
// Spring Boot 기본 오류 처리기(@Order(-1))보다 먼저 받는다
// ErrorWebExceptionHandler로 만들면 기본 오류 처리기가 등록되지 않으므로(@ConditionalOnMissingBean) WebExceptionHandler로 둔다
@Order(-2)
public class GatewayErrorHandler implements WebExceptionHandler {

	// 504·503·404 응답의 code. 프론트가 오류 원인을 구분하는 값이라 이 한곳에서만 정의한다
	public static final String CODE_GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT";
	public static final String CODE_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
	public static final String CODE_NOT_FOUND = "NOT_FOUND";

	private static final String MESSAGE_GATEWAY_TIMEOUT = "서버 응답이 늦어요. 잠시 뒤 다시 시도해 주세요.";
	private static final String MESSAGE_SERVICE_UNAVAILABLE = "서버에 연결할 수 없어요. 잠시 뒤 다시 시도해 주세요.";
	private static final String MESSAGE_NOT_FOUND = "요청한 주소를 찾을 수 없어요.";

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
		// 서비스 응답을 이미 보내기 시작했으면 상태 코드와 본문을 바꿀 수 없다
		if (exchange.getResponse().isCommitted()) {
			return Mono.error(ex);
		}
		// NettyRoutingFilter는 응답 시간 초과를 TimeoutException을 원인으로 담은 504 ResponseStatusException으로 바꾼다
		if (hasCause(ex, TimeoutException.class)) {
			return RsDataResponseWriter.write(exchange.getResponse(), HttpStatus.GATEWAY_TIMEOUT,
				CODE_GATEWAY_TIMEOUT, MESSAGE_GATEWAY_TIMEOUT);
		}
		// 연결 거부(AnnotatedConnectException)와 연결 시간 초과(ConnectTimeoutException)는 둘 다 ConnectException의 자식이다
		// 멈춘 컨테이너는 Docker 내부 DNS에서 이름이 빠져 UnknownHostException이 나므로 함께 503으로 본다
		if (hasCause(ex, ConnectException.class) || hasCause(ex, UnknownHostException.class)) {
			return RsDataResponseWriter.write(exchange.getResponse(), HttpStatus.SERVICE_UNAVAILABLE,
				CODE_SERVICE_UNAVAILABLE, MESSAGE_SERVICE_UNAVAILABLE);
		}
		// 라우트가 없는 주소. 서비스가 보낸 404 응답은 예외가 아니라 여기로 오지 않는다
		if (ex instanceof ResponseStatusException statusException
			&& statusException.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
			return RsDataResponseWriter.write(exchange.getResponse(), HttpStatus.NOT_FOUND,
				CODE_NOT_FOUND, MESSAGE_NOT_FOUND);
		}
		return Mono.error(ex);
	}

	// 예외가 다른 예외에 감싸여 올 수 있어 원인 체인을 따라가며 찾는다
	private boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
		for (Throwable current = ex; current != null; current = current.getCause()) {
			if (type.isInstance(current)) {
				return true;
			}
		}
		return false;
	}
}
