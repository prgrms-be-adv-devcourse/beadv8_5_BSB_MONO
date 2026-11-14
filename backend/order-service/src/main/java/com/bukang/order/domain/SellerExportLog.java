package com.bukang.order.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자가 개인정보가 든 목록(참가자 명단·키트 배송 목록)을 내려받은 기록 (BUK-228)
 * 받은 시각은 create_date다. 보관 기간(1년, 시안 S03 안내 문구)이 지난 줄을 지우는 일은 아직 없다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_SELLER_EXPORT_LOG",
	indexes = {
		@Index(name = "idx_seller_export_log_seller", columnList = "seller_id, id")
	}
)
public class SellerExportLog extends BaseIdAndTime {
	@Column(name = "seller_id", nullable = false)
	private long sellerId;

	// 내려받은 회원 (판매자 계정의 회원 ID)
	@Column(name = "member_id", nullable = false)
	private long memberId;

	// 대회를 고르지 않고 받은 키트 배송 목록이면 null
	@Column(name = "race_id")
	private Long raceId;

	@Enumerated(STRING)
	@Column(name = "export_type", nullable = false, columnDefinition = "varchar(20)")
	private SellerExportType exportType;

	@Column(name = "reason", nullable = false, length = 200)
	private String reason;

	@Column(name = "row_count", nullable = false)
	private int rowCount;

	public static SellerExportLog of(long sellerId, long memberId, Long raceId, SellerExportType exportType,
		String reason, int rowCount) {
		SellerExportLog log = new SellerExportLog();
		log.sellerId = sellerId;
		log.memberId = memberId;
		log.raceId = raceId;
		log.exportType = exportType;
		log.reason = reason;
		log.rowCount = rowCount;
		return log;
	}
}
