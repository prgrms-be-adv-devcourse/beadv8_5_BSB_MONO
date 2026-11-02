package com.bukang.queue.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.bukang.queue.domain.exception.QueueMemberNotFoundException;
import com.bukang.queue.dto.QueueStatusDto;
import com.bukang.queue.out.QueueRedisRepository;
import com.bukang.queue.out.QueueRedisRepository.Position;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueueStatusUseCase {
	private final QueueRedisRepository queueRedisRepository;

	// 대기 화면이 몇 초마다 부른다. 대회·종목 복제본(DB)은 읽지 않고 Redis만 읽는다
	// 그래서 없는 대회·종목 ID로 부르면 줄에 없는 회원과 같이 404다
	public QueueStatusDto getStatus(long memberId, long raceId, long raceCourseId, LocalDateTime now) {
		Position position = queueRedisRepository.findPosition(raceId, raceCourseId, memberId, now);
		return switch (position.state()) {
			case WAITING -> QueueStatusDto.waiting(position.rank(), position.total());
			case ACTIVE -> QueueStatusDto.active();
			case NONE -> throw new QueueMemberNotFoundException();
		};
	}
}
