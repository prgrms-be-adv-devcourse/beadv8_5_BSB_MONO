package com.bukang.member.in;

import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

final class ClientIp {
	private static final String REAL_IP_HEADER = "X-Real-IP";

	private ClientIp() {
	}

	/**
	 * Nginx가 접속자 IP로 덮어써 넣는 X-Real-IP를 쓴다. 클라이언트가 보낸 값은 Nginx에서 사라진다
	 * X-Forwarded-For는 클라이언트가 보낸 값 뒤에 이어 붙는 방식이라 맨 앞 값을 위조할 수 있어 쓰지 않는다
	 * Nginx가 없는 로컬은 접속 주소를 쓴다
	 */
	static String of(HttpServletRequest request) {
		String realIp = request.getHeader(REAL_IP_HEADER);
		return StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr();
	}
}
