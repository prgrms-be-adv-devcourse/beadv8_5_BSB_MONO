package com.bukang.member.app;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 현재 트랜잭션이 커밋된 뒤에만 작업을 실행한다. 롤백되면 실행하지 않는다
 */
final class AfterCommit {
	private AfterCommit() {
	}

	static void run(Runnable task) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				task.run();
			}
		});
	}
}
