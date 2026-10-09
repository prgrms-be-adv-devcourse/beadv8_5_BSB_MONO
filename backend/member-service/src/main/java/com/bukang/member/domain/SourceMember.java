package com.bukang.member.domain;

import static jakarta.persistence.GenerationType.*;
import static lombok.AccessLevel.*;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.bukang.common.shared.member.domain.BaseMember;
import com.bukang.member.config.crypto.EncryptedStringConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원 Context가 원본으로 관리하는 회원 (ID 자동 생성, 생성/수정 시각 자동 기록)
 * 비밀번호, 이메일, 휴대폰 번호처럼 다른 Context에 복제하지 않는 정보도 여기에 둔다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter(value = PROTECTED)
@NoArgsConstructor
public abstract class SourceMember extends BaseMember {
	@Id
	@GeneratedValue(strategy = IDENTITY)
	private int id;
	@CreatedDate
	private LocalDateTime createDate;
	@LastModifiedDate
	private LocalDateTime modifyDate;

	private String password;
	@Column(unique = true)
	private String email;

	@Convert(converter = EncryptedStringConverter.class) // DB 저장 시 AES 암호화, 조회 시 복호화
	@Column(length = 255)
	private String phone;

	@Column(name = "phone_hash", unique = true, length = 64) // 휴대폰 번호 조회, 중복 검사용 블라인드 인덱스
	private String phoneHash;

	public SourceMember(
		String username,
		String password,
		String nickname,
		String email,
		String phone,
		String phoneHash
	) {
		super(username, nickname);
		this.password = password;
		this.email = email;
		this.phone = phone;
		this.phoneHash = phoneHash;
	}
}
