package com.bukang.product.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseEntity;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 서비스가 판매자 이벤트로 복제해 보관하는 판매자
 * ID와 신청·심사 시각은 원본 판매자 값을 그대로 쓴다. 정산 계좌, 연락처 같은 정보는 원본에만 둔다.
 * 상호명·대표자·사업자등록번호는 대회 상세에 공개하는 판매자 정보라서 복제한다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "PRODUCT_SELLER",
	// 회원의 승인된 판매자 조회 (대회 등록 때 판매자 확인)
	indexes = @Index(name = "idx_product_seller_member", columnList = "member_id, status")
)
public class ProductSeller extends BaseEntity {
	@Id
	private long id;

	// 판매자로 등록한 회원. 반려 뒤 재신청하면 한 회원에 행이 여러 개일 수 있다
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false)
	private ProductMember member;

	@Enumerated(STRING)
	@Column(name = "seller_type", nullable = false, columnDefinition = "varchar(20)")
	private SellerType sellerType;

	// 대회 상세의 판매자 정보에 보여 준다. 상호명·사업자등록번호는 사업자만 있다
	@Column(length = 100)
	private String businessName;

	@Column(length = 20)
	private String businessNumber;

	// 사업자는 대표자, 비사업자는 성명
	@Column(nullable = false, length = 50)
	private String representativeName;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private SellerStatus status;

	// 원본의 등록 신청 일시
	@Column(nullable = false)
	private LocalDateTime requestedAt;

	// 원본의 승인·반려 일시. 심사 전에는 null
	private LocalDateTime reviewedAt;

	public ProductSeller(
		long id,
		ProductMember member,
		SellerType sellerType,
		String businessName,
		String businessNumber,
		String representativeName,
		SellerStatus status,
		LocalDateTime requestedAt,
		LocalDateTime reviewedAt
	) {
		this.id = id;
		this.member = member;
		this.sellerType = sellerType;
		this.businessName = businessName;
		this.businessNumber = businessNumber;
		this.representativeName = representativeName;
		this.status = status;
		this.requestedAt = requestedAt;
		this.reviewedAt = reviewedAt;
	}

	public boolean isApproved() {
		return status == SellerStatus.APPROVED;
	}

	@Override
	public LocalDateTime getCreateDate() {
		return requestedAt;
	}

	// 심사 전에는 신청 일시가 마지막으로 바뀐 시각이다
	@Override
	public LocalDateTime getModifyDate() {
		return reviewedAt != null ? reviewedAt : requestedAt;
	}
}
