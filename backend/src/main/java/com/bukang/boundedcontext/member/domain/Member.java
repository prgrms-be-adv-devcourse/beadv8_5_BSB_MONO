package com.bukang.boundedcontext.member.domain;

import com.bukang.shared.member.domain.SourceMember;
import com.bukang.shared.member.dto.MemberDto;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
// username, nickname은 BaseMember(복제본과 공유)에 있어 unique를 원본 테이블에서만 건다
@Table(name = "MEMBER_MEMBER", uniqueConstraints = {
	@UniqueConstraint(columnNames = "username"),
	@UniqueConstraint(columnNames = "nickname")
})
public class Member extends SourceMember {
	public Member(
		String username,
		String email,
		String password,
		String nickname,
		String phone,
		String phoneHash
	) {
		super(username, password, nickname, email, phone, phoneHash);
	}

	public MemberDto toDto() {
		return new MemberDto(
			getId(),
			getCreateDate(),
			getModifyDate(),
			getUsername(),
			getNickname(),
			getEmail(),
			getPhone()
		);
	}
}
