package com.bukang.order.out.crew;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: 회원 서비스를 띄우지 않고 파티를 개발할 때, 누구나 오래전에 가입한 크루원이고 크루원은 10명이다 (BUK-35)
 * order.clients.crew.mode=fake라고 적었을 때만 쓴다. 관리자 확인처럼 mode가 빠지면 켜지지 않아,
 * 설정을 빠뜨린 운영에서 아무나 파티에 합류하는 대신 앱이 뜨지 않는다
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "order.clients.crew", name = "mode", havingValue = "fake")
public class FakeCrewAdapter implements CrewPort {
	static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);
	static final int MEMBER_COUNT = 10;

	public FakeCrewAdapter() {
		log.warn("크루 확인은 가짜 응답(FakeCrewAdapter)을 씁니다. 누구나 크루원입니다. 실제 회원 서비스는 order.clients.crew.mode=http");
	}

	@Override
	public Optional<CrewMembership> findMembership(long crewId, long memberId) {
		return Optional.of(new CrewMembership(true, JOINED_AT));
	}

	@Override
	public OptionalInt countMembers(long crewId) {
		return OptionalInt.of(MEMBER_COUNT);
	}

	@Override
	public Optional<LocalDateTime> findPartyRestrictionEnd(long crewId) {
		return Optional.empty();
	}
}
