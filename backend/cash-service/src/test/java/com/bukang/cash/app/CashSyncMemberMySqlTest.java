package com.bukang.cash.app;

import org.junit.jupiter.api.DisplayName;
import org.springframework.context.annotation.Import;

import com.bukang.cash.TestcontainersConfiguration;

/**
 * CashSyncMemberUseCaseTest의 테스트를 DB만 MySQL로 바꿔 한 번 더 돌린다
 * 동시에 처리되는 회원 메시지는 격리 수준에 따라 결과가 달라서, 운영과 같은 MySQL에서도 확인한다
 */
@DisplayName("회원 동기화 (MySQL)")
@Import(TestcontainersConfiguration.class)
class CashSyncMemberMySqlTest extends CashSyncMemberUseCaseTest {
}
