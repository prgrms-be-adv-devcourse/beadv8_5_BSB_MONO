package com.bukang.common.global.web;

/**
 * 서비스끼리 내부 API(/internal/**)를 부를 때 보내는 인증 키 헤더.
 * 받는 쪽과 보내는 쪽이 같은 이름을 쓰도록 모든 서비스가 이 상수를 쓴다. 키 값은 각 서비스의 internal.api-key 설정이다
 */
public final class InternalKeyHeader {
	public static final String NAME = "X-Internal-Key";

	private InternalKeyHeader() {
	}
}
