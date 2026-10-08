package com.bukang.common.event.member;

import java.time.LocalDateTime;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 다른 Context가 회원 이벤트로 복제해 보관하는 회원 정보 (ID와 생성/수정 시각은 원본 회원 값을 그대로 사용)
 * 이메일처럼 특정 Context만 필요한 정보는 그 Context의 하위 엔티티에 따로 추가한다.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor
public abstract class ReplicaMember extends BaseMember {
	@Id
	private int id;
	private LocalDateTime createDate;
	private LocalDateTime modifyDate;

	public ReplicaMember(
		int id,
		LocalDateTime createDate,
		LocalDateTime modifyDate,
		String username,
		String nickname
	) {
		super(username, nickname);
		this.id = id;
		this.createDate = createDate;
		this.modifyDate = modifyDate;
	}
}
