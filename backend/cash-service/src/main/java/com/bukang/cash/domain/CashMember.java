package com.bukang.cash.domain;

import java.time.LocalDateTime;

import com.bukang.common.event.member.ReplicaMember;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 캐시 서비스가 회원 이벤트(MemberJoinedEvent)로 복제해 보관하는 회원
 * ID와 생성/수정 시각은 원본 회원(member-service) 값을 그대로 쓴다.
 */
@Entity
@NoArgsConstructor
@Getter
@Table(name = "CASH_MEMBER")
public class CashMember extends ReplicaMember {
	public CashMember(
		int id,
		LocalDateTime createDate,
		LocalDateTime modifyDate,
		String username,
		String nickname
	) {
		super(id, createDate, modifyDate, username, nickname);
	}
}
