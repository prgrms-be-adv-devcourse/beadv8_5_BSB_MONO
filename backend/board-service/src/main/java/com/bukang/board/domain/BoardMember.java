package com.bukang.board.domain;

import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시판이 회원 이벤트로 복사해 보관하는 회원 (글쓴이의 닉네임, 판매자 표시, 탈퇴 여부)
 * ID와 생성/수정 시각은 원본 회원(member-service) 값을 그대로 쓴다. 글 목록마다 회원 서비스를 부르지 않으려고 둔다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "BOARD_MEMBER")
public class BoardMember extends BaseEntity {
	// 탈퇴한 회원은 닉네임 대신 이 이름을 보여 준다
	public static final String WITHDRAWN_NICKNAME = "탈퇴한 회원";

	@Id
	private long id;

	private String nickname;

	// 지금 판매자 승인 상태. 글을 쓴 때가 아니라 지금 상태를 따른다
	@Column(name = "seller_approved", nullable = false)
	private boolean sellerApproved;

	@Column(nullable = false)
	private boolean withdrawn;

	private LocalDateTime createDate;
	private LocalDateTime modifyDate;

	public BoardMember(long id, String nickname, LocalDateTime createDate, LocalDateTime modifyDate) {
		this.id = id;
		this.nickname = nickname;
		this.createDate = createDate;
		this.modifyDate = modifyDate;
	}

	// 탈퇴 이벤트가 가입 이벤트보다 먼저 온 경우: 닉네임을 모르므로 탈퇴한 회원으로 만든다
	public static BoardMember withdrawnOf(long id, LocalDateTime withdrawnAt) {
		BoardMember member = new BoardMember(id, WITHDRAWN_NICKNAME, withdrawnAt, withdrawnAt);
		member.withdrawn = true;
		return member;
	}

	public void syncProfile(String nickname, LocalDateTime modifyDate) {
		this.nickname = nickname;
		this.modifyDate = modifyDate;
	}

	public void changeSeller(boolean approved) {
		this.sellerApproved = approved;
	}

	// 탈퇴는 되돌아가지 않으므로 수정 시각과 상관없이 표시한다
	public void withdraw(LocalDateTime withdrawnAt) {
		this.withdrawn = true;
		if (modifyDate == null || withdrawnAt.isAfter(modifyDate)) {
			this.modifyDate = withdrawnAt;
		}
	}
}
