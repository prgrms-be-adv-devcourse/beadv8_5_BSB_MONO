package com.bukang.common.event.member;

import static lombok.AccessLevel.*;

import com.bukang.common.jpa.BaseEntity;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원 Context(SourceMember)와 다른 Context(ReplicaMember)가 공유하는 회원 공통 필드
 * 다른 Context에 복제돼도 되는 공개 정보만 둔다. 비밀번호, 연락처 같은 민감 정보는 SourceMember에 둔다.
 */
@MappedSuperclass
@Getter
@Setter(value = PROTECTED)
@NoArgsConstructor
public abstract class BaseMember extends BaseEntity {
	// unique 제약은 원본(Member 엔티티)에만 건다. 복제본은 이벤트 순서에 따라 잠깐 값이 겹칠 수 있다
	private String username;
	private String nickname;

	public BaseMember(String username, String nickname) {
		this.username = username;
		this.nickname = nickname;
	}

	public boolean isSystem() {
		return "system".equals(username);
	}
}
