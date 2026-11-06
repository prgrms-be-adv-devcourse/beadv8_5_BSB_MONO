package com.bukang.queue.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대기열 승격 설정 (queue.*).
 * 승격 주기는 개인 줄과 파티 줄이 같고(스케줄러 하나), 한 번에 승격할 인원과 입장 시간은 줄마다 따로 둔다.
 *
 * @param schedule 승격 주기와 개인 줄에서 한 번에 승격할 인원
 * @param active 개인 줄에서 승격한 회원이 입장(active)해 있는 시간
 * @param party 파티 줄의 승격 인원과 입장 시간 (queue.party.*)
 */
@ConfigurationProperties("queue")
public record QueueProperties(Schedule schedule, Active active, Party party) {

	public QueueProperties {
		if (schedule == null || active == null || party == null) {
			throw new IllegalArgumentException("queue.schedule, queue.active, queue.party를 설정해야 합니다.");
		}
	}

	/**
	 * @param time 승격 주기 (숫자만 쓰면 ms). 스케줄러는 @Scheduled에서 이 값을 직접 읽는다
	 * @param size 대기열(대회·종목)마다 한 번에 승격할 인원
	 */
	public record Schedule(Duration time, int size) {
		public Schedule {
			if (time == null || time.isNegative() || time.isZero()) {
				throw new IllegalArgumentException("queue.schedule.time은 0보다 커야 합니다: " + time);
			}
			if (size <= 0) {
				throw new IllegalArgumentException("queue.schedule.size는 0보다 커야 합니다: " + size);
			}
		}
	}

	/**
	 * @param time 활성 시간 (숫자만 쓰면 ms). 승격 시각 + 이 시간이 active 끝 시각이다
	 */
	public record Active(Duration time) {
		public Active {
			if (time == null || time.isNegative() || time.isZero()) {
				throw new IllegalArgumentException("queue.active.time은 0보다 커야 합니다: " + time);
			}
		}
	}

	/**
	 * 파티 줄 승격 설정 (queue.party.*). 승격 주기는 개인 줄과 같은 queue.schedule.time을 쓴다.
	 *
	 * @param schedule 파티 줄(대회·종목)마다 한 번에 승격할 파티 수
	 * @param active 승격한 파티장이 입장(active)해 있는 시간
	 */
	public record Party(PartySchedule schedule, Active active) {
		public Party {
			if (schedule == null || active == null) {
				throw new IllegalArgumentException("queue.party.schedule과 queue.party.active를 설정해야 합니다.");
			}
		}
	}

	/**
	 * @param size 파티 줄(대회·종목)마다 한 번에 승격할 파티 수 (줄에는 파티장 회원 ID가 서므로 파티장 수와 같다)
	 */
	public record PartySchedule(int size) {
		public PartySchedule {
			if (size <= 0) {
				throw new IllegalArgumentException("queue.party.schedule.size는 0보다 커야 합니다: " + size);
			}
		}
	}
}
