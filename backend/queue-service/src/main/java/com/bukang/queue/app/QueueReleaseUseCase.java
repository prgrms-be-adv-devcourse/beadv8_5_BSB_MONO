package com.bukang.queue.app;

import org.springframework.stereotype.Service;

import com.bukang.queue.domain.QueueLine;
import com.bukang.queue.out.QueueRedisRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대기열 입장(active) 해제. 개인 예매는 결제 화면을, 파티 예매는 크루 [대회 참가] 화면을 벗어날 때 프론트가 부른다.
 * 해제하지 않으면 입장 시간이 끝날 때까지 다시 줄을 설 수 없다(등록이 409). 해제한 뒤 다시 등록하면 waiting 맨 뒤에 선다.
 * 입장 중이 아니어도(이미 해제함, 입장 시간이 끝남, 줄에서 기다리는 중) 오류 없이 끝난다.
 * 페이지를 벗어날 때 여러 번 불리거나 늦게 불려도 같은 결과여야 하기 때문이다.
 * 승격은 주기마다 정해진 인원을 옮기므로, 해제해도 다음 사람의 입장이 앞당겨지지는 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueReleaseUseCase {
	private final QueueRedisRepository queueRedisRepository;

	// 결제 화면을 벗어날 때. 개인 줄에서 회원의 입장을 푼다
	public void release(long memberId, long raceId, long raceCourseId) {
		release(QueueLine.INDIVIDUAL, memberId, raceId, raceCourseId);
	}

	// 크루 [대회 참가] 화면을 벗어날 때. 파티 줄에서 파티장 회원 ID의 입장을 푼다 (파티장인지는 확인하지 않는다, 등록과 같다)
	public void releaseParty(long leaderMemberId, long raceId, long raceCourseId) {
		release(QueueLine.PARTY, leaderMemberId, raceId, raceCourseId);
	}

	private void release(QueueLine line, long memberId, long raceId, long raceCourseId) {
		boolean removed = queueRedisRepository.removeActive(line, raceId, raceCourseId, memberId);
		// 확인용 로그. dev(com.bukang: debug)에서만 보이고 prod(info)에는 남지 않는다
		if (removed) {
			log.debug("대기열 입장을 해제했습니다. line={}, raceId={}, raceCourseId={}, memberId={}",
				line, raceId, raceCourseId, memberId);
		}
	}
}
