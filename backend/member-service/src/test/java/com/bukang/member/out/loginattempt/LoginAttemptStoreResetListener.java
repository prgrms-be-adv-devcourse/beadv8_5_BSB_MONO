package com.bukang.member.out.loginattempt;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * 매 테스트 전에 메모리 로그인 시도 횟수를 비운다 (META-INF/spring.factories로 모든 스프링 테스트에 붙는다)
 * 같은 컨텍스트를 같이 쓰는 테스트 클래스끼리, 같은 이메일의 실패 횟수가 이어지지 않게 한다
 */
public class LoginAttemptStoreResetListener implements TestExecutionListener {
	@Override
	public void beforeTestMethod(TestContext testContext) {
		testContext.getApplicationContext().getBeanProvider(InMemoryLoginAttemptStore.class)
			.ifAvailable(InMemoryLoginAttemptStore::clearAll);
	}
}
