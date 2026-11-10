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

	// 판매자 이벤트의 시각(심사 시각, 없으면 신청 시각). 옛 이벤트가 늦게 와도 덮어쓰지 않게 비교한다
	@Column(name = "seller_changed_at")
	private LocalDateTime sellerChangedAt;

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

	// 판매자 이벤트가 가입 이벤트보다 먼저 온 경우: 닉네임은 가입 이벤트가 채운다
	public static BoardMember sellerOf(long id, boolean approved, LocalDateTime changedAt) {
		BoardMember member = new BoardMember();
		member.id = id;
		member.sellerApproved = approved;
		member.sellerChangedAt = changedAt;
		return member;
	}

	// 판매자 여부는 건드리지 않는다 (판매자 이벤트가 먼저 와서 켜 둔 값을 늦은 가입 이벤트가 지우면 안 된다)
	public void syncProfile(String nickname, LocalDateTime createDate, LocalDateTime modifyDate) {
		this.nickname = nickname;
		if (this.createDate == null) {
			this.createDate = createDate;
		}
		this.modifyDate = modifyDate;
	}

	// 저장된 시각보다 옛 이벤트면 버린다. 시각이 없는 이벤트는 순서를 못 따지므로 그대로 반영한다
	public void changeSeller(boolean approved, LocalDateTime changedAt) {
		if (changedAt != null && sellerChangedAt != null && changedAt.isBefore(sellerChangedAt)) {
			return;
		}
		this.sellerApproved = approved;
		if (changedAt != null) {
			this.sellerChangedAt = changedAt;
		}
	}

	public boolean isProfileSynced() {
		return modifyDate != null;
	}

	// 탈퇴는 되돌아가지 않으므로 수정 시각과 상관없이 표시한다
	public void withdraw(LocalDateTime withdrawnAt) {
		this.withdrawn = true;
		if (modifyDate == null || withdrawnAt.isAfter(modifyDate)) {
			this.modifyDate = withdrawnAt;
		}
	}
}
