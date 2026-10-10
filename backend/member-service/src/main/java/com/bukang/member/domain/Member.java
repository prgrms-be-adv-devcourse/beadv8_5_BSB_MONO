package com.bukang.member.domain;

import java.time.LocalDate;
import java.time.Period;

import com.bukang.member.dto.MemberDto;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
// nickname은 BaseMember(복제본과 공유)에 있어 unique, 길이, NOT NULL을 원본 테이블에서만 건다
@Table(name = "MEMBER_MEMBER", uniqueConstraints = {
	@UniqueConstraint(columnNames = "nickname")
})
@AttributeOverride(name = "nickname", column = @Column(name = "nickname", nullable = false, length = 30))
public class Member extends SourceMember {
	private static final int MIN_JOIN_AGE = 14;

	public Member(
		String email,
		String password,
		String name,
		String nickname,
		String phone,
		String phoneHash,
		LocalDate birthDate,
		Gender gender
	) {
		super(email, password, name, nickname, phone, phoneHash, birthDate, gender);
	}

	// 만 나이 기준. 생일 당일부터 한 살 더한다
	public static boolean isJoinableAge(LocalDate birthDate, LocalDate today) {
		return Period.between(birthDate, today).getYears() >= MIN_JOIN_AGE;
	}

	public MemberDto toDto() {
		return new MemberDto(
			getId(),
			getCreateDate(),
			getModifyDate(),
			getNickname(),
			getEmail(),
			getPhone()
		);
	}
}
