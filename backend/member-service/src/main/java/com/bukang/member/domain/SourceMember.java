package com.bukang.member.domain;

import static jakarta.persistence.GenerationType.*;
import static lombok.AccessLevel.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.bukang.common.global.crypto.EncryptedStringConverter;
import com.bukang.common.shared.member.domain.BaseMember;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원 Context가 원본으로 관리하는 회원 (ID 자동 생성, 생성/수정 시각 자동 기록)
 * 비밀번호, 이메일, 휴대폰 번호처럼 다른 Context에 복제하지 않는 정보도 여기에 둔다.
 * 컬럼 이름은 피그잼 ERD(09 ERD · 최신 통합본)의 member 표를 따른다. 자바 필드 이름은 BaseEntity의 getter 이름에 맞춘다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter(value = PROTECTED)
@NoArgsConstructor
public abstract class SourceMember extends BaseMember {
	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "member_id")
	private long id;
	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createDate;
	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime modifyDate;

	// 개인정보 칸은 탈퇴하면 비우므로(NULL) NULL을 허용한다. 활성 회원의 빈칸은 Member의 CHECK가 막는다 (BE-022)
	@Column // 로그인 아이디. unique 제약은 Member의 uk_member_email
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String password;

	@Column
	private String name;

	@Convert(converter = EncryptedStringConverter.class) // DB 저장 시 AES 암호화, 조회 시 복호화
	@Column(length = 255)
	private String phone;

	// 휴대폰 번호 조회, 중복 검사용 블라인드 인덱스. unique 제약은 Member의 uk_member_phone_hash
	@Column(name = "phone_hash", length = 64)
	private String phoneHash;

	@Column(name = "birth_date") // 만 14세 미만 가입 불가 확인용 (정책 1장 1-4)
	private LocalDate birthDate;

	// Hibernate는 enum을 MySQL ENUM 타입으로 만들므로, 값이 늘어도 ALTER가 필요 없게 VARCHAR로 고정한다
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 10)
	private Gender gender;

	// 본인인증은 최종 프로젝트에서 붙인다. 그 전까지는 비어 있다
	@Column(name = "ci_hash", length = 64)
	private String ciHash;

	@Column(name = "verified_at")
	private LocalDateTime verifiedAt;

	// 지금 상태 (판단용). DB 기본값을 'ACTIVE'로 둬 기존 행이 있는 표에 칸을 더해도 채워진다 (BE-022)
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@ColumnDefault("'ACTIVE'")
	@Column(nullable = false, length = 20)
	private MemberStatus status = MemberStatus.ACTIVE;

	// 탈퇴한 시각 (기록용). 상태와 같은 메서드(Member.withdraw)에서 함께 바꾼다
	@Column(name = "withdrawn_at")
	private LocalDateTime withdrawnAt;

	public SourceMember(
		String email,
		String password,
		String name,
		String nickname,
		String phone,
		String phoneHash,
		LocalDate birthDate,
		Gender gender
	) {
		super(nickname);
		this.email = email;
		this.password = password;
		this.name = name;
		this.phone = phone;
		this.phoneHash = phoneHash;
		this.birthDate = birthDate;
		this.gender = gender;
	}
}
