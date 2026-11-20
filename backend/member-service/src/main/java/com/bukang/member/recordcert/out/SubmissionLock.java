package com.bukang.member.recordcert.out;

import java.util.Optional;

/**
 * 같은 참가권의 기록증 제출이 동시에 처리되지 않게 참가권 단위로 잠근다 (BUK-274, 운영은 Redis)
 * 잠금은 30초 뒤 저절로 풀려, 처리 중 서버가 죽어도 참가권이 영영 막히지 않는다
 * 잠금 저장소가 응답하지 않으면 RecordCertCheckUnavailableException(503)을 던진다
 */
public interface SubmissionLock {
	// 잠갔으면 푸는 데 쓸 토큰, 이미 잠겨 있으면 빈 값
	Optional<String> tryLock(long orderItemId);

	// 토큰이 같을 때만 푼다. 30초가 지나 다른 요청이 잡은 잠금을 풀지 않게
	void unlock(long orderItemId, String token);
}
