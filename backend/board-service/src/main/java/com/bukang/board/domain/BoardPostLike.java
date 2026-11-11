package com.bukang.board.domain;

import static lombok.AccessLevel.PROTECTED;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 좋아요 한 번. (글, 회원) 유니크 제약이 중복을 막는다
 * 행은 BoardPostLikeRepository.insertIgnore로만 넣는다 (이미 있으면 예외 없이 0행)
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "BOARD_POST_LIKE",
	uniqueConstraints = @UniqueConstraint(name = "uk_board_like_post_member", columnNames = {"post_id", "member_id"})
)
public class BoardPostLike extends BaseIdAndTime {
	@Column(name = "post_id", nullable = false)
	private long postId;

	@Column(name = "member_id", nullable = false)
	private long memberId;
}
