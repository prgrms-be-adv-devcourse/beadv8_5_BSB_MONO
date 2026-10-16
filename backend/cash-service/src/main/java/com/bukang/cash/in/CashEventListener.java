package com.bukang.cash.in;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.bukang.cash.app.CashFacade;
import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.member.event.MemberJoinedEvent;
import com.bukang.common.shared.member.event.MemberModifiedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CashEventListener {
	private final JsonConverter jsonConverter;
	private final CashFacade cashFacade;

	@KafkaListener(topics = KafkaTopics.MEMBER_MEMBER_JOINED, groupId = "CashEventListener__handleMemberJoined__1")
	public void handleMemberJoined(String message) {
		MemberJoinedEvent event = jsonConverter.fromJson(message, MemberJoinedEvent.class);
		cashFacade.syncMember(event.id(), event.createDate(), event.modifyDate(), event.nickname());
	}

	@KafkaListener(topics = KafkaTopics.MEMBER_MODIFIED, groupId = "CashEventListener__handleMemberModified__1")
	public void handleMemberModified(String message) {
		MemberModifiedEvent event = jsonConverter.fromJson(message, MemberModifiedEvent.class);
		cashFacade.syncMember(event.id(), event.createDate(), event.modifyDate(), event.nickname());
	}
}
