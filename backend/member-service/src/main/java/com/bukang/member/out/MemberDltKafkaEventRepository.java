package com.bukang.member.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.member.domain.MemberDltKafkaEvent;

public interface MemberDltKafkaEventRepository extends JpaRepository<MemberDltKafkaEvent, Long> {
	Optional<MemberDltKafkaEvent> findByMessageId(String messageId);
}
