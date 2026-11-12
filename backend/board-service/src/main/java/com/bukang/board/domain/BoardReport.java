package com.bukang.board.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 신고 한 건. 회원 한 명이 글·댓글 하나를 한 번만 신고할 수 있다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "BOARD_REPORT", uniqueConstraints = @UniqueConstraint(name = "uk_board_report_target_reporter",
	columnNames = {"target_type", "target_id", "reporter_id"}))
public class BoardReport extends BaseIdAndTime {
	@Enumerated(STRING)
	@Column(name = "target_type", nullable = false, columnDefinition = "varchar(20)")
	private ReportTargetType targetType;

	@Column(name = "target_id", nullable = false)
	private long targetId;

	@Column(name = "reporter_id", nullable = false)
	private long reporterId;

	@Enumerated(STRING)
	@Column(nullable = false, columnDefinition = "varchar(20)")
	private ReportReason reason;
}
