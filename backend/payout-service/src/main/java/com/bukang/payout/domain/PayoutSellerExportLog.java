package com.bukang.payout.domain;

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
 * 판매자가 정산 자료(정산 내역·건별 정산)를 CSV로 내려받은 기록
 * 받은 시각은 create_date다. 개인정보가 아니라 사유는 받지 않고 누가(판매자·회원) 언제 무엇을 몇 줄 받았는지만 남긴다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "PAYOUT_SELLER_EXPORT_LOG",
	indexes = {
		@Index(name = "idx_payout_seller_export_log_seller", columnList = "seller_id, id")
	}
)
public class PayoutSellerExportLog extends BaseIdAndTime {
	@Column(name = "seller_id", nullable = false)
	private long sellerId;

	// 내려받은 회원 (판매자 계정의 회원 ID)
	@Column(name = "member_id", nullable = false)
	private long memberId;

	// enum 칸은 DB ENUM이다 (PayoutEnumColumnTest). 마이그레이션 SQL이 ENUM 목록을 정하고, 엔티티는 길이만 준다
	@Enumerated(STRING)
	@Column(name = "export_type", nullable = false, length = 20)
	private PayoutExportType exportType;

	@Column(name = "row_count", nullable = false)
	private int rowCount;

	public static PayoutSellerExportLog of(long sellerId, long memberId, PayoutExportType exportType, int rowCount) {
		PayoutSellerExportLog log = new PayoutSellerExportLog();
		log.sellerId = sellerId;
		log.memberId = memberId;
		log.exportType = exportType;
		log.rowCount = rowCount;
		return log;
	}
}
