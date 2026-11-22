package com.bukang.product.domain;

import static lombok.AccessLevel.PROTECTED;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 홈 배너로 고른 대회 하나 (CRU-147). 관리자가 고른 순서대로 홈 맨 위에 보인다 (최대 5개)
 * 배너 이미지는 대회 대표 이미지를 그대로 써서 저장하지 않는다 (대표 이미지는 대회마다 1장)
 * 목록은 늘 전체를 한 번에 바꾸므로 행을 고치지 않고 지우고 다시 만든다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "product_home_banner",
	uniqueConstraints = @UniqueConstraint(name = "uk_product_home_banner_race", columnNames = "race_id")
)
@AttributeOverride(name = "id", column = @Column(name = "home_banner_id"))
@AttributeOverride(name = "createDate", column = @Column(name = "created_at", nullable = false, updatable = false))
@AttributeOverride(name = "modifyDate", column = @Column(name = "updated_at", nullable = false))
public class HomeBanner extends BaseIdAndTime {
	// 같은 서비스의 대회지만 배너는 대회 ID만 쓰면 되어 연관관계를 두지 않는다 (FK는 테이블에 있다)
	@Column(name = "race_id", nullable = false)
	private long raceId;

	// 홈에 보이는 순서 (0부터)
	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	// 목록을 저장한 관리자의 회원 ID
	@Column(name = "created_by", nullable = false)
	private long createdBy;

	public HomeBanner(long raceId, int displayOrder, long createdBy) {
		this.raceId = raceId;
		this.displayOrder = displayOrder;
		this.createdBy = createdBy;
	}
}
