package com.bukang.cash.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.bukang.common.global.web.InternalKeyHeader;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 내부 API는 게이트웨이·Nginx가 밖으로 넘기지 않지만, 서비스 포트에 직접 닿으면 누구나 결제·환불·정산 지급을 부를 수 있다.
 * 그래서 서비스끼리 나눠 가진 키를 헤더로 받아 확인한다. 어느 주소에 거는지는 InternalKeyFilterConfig에 있다
 */
@Slf4j
public class InternalKeyFilter extends OncePerRequestFilter {
	private static final String UNAUTHORIZED_BODY = """
		{"status":401,"code":"CASH_INTERNAL_KEY_INVALID","message":"내부 API 인증 키가 없거나 맞지 않습니다.","data":null}""";

	private final byte[] key;

	public InternalKeyFilter(String key) {
		// 빈 키를 허용하면 빈 헤더를 보낸 요청이 통과하므로, 키 없이는 앱이 뜨지 않게 한다
		if (key == null || key.isBlank()) {
			throw new IllegalStateException("internal.api-key가 비어 있습니다. 환경 변수 INTERNAL_API_KEY를 넣어 주세요.");
		}
		this.key = key.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		String given = request.getHeader(InternalKeyHeader.NAME);
		// 글자를 앞에서부터 비교하다 멈추면 걸린 시간으로 키를 한 글자씩 알아낼 수 있어, 길이와 상관없이 끝까지 비교한다
		if (given != null && MessageDigest.isEqual(key, given.getBytes(StandardCharsets.UTF_8))) {
			chain.doFilter(request, response);
			return;
		}
		// 배포 뒤 어느 서비스가 키를 빠뜨렸는지(없음) 또는 키 값이 어긋났는지(다름) 찾으려고 남긴다. 키 값은 남기지 않는다
		log.warn("내부 API 인증 실패. path={}, remote={}, key={}",
			request.getRequestURI(), request.getRemoteAddr(), given == null ? "없음" : "다름");
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(UNAUTHORIZED_BODY);
	}
}
