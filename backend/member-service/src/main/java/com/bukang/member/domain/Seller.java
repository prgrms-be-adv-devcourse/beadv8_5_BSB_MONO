package com.bukang.member.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseEntity;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.common.shared.seller.event.SellerChangedEvent;
import com.bukang.member.domain.exception.SellerAlreadyReviewedException;
import com.bukang.member.domain.exception.SellerNotReviewedException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자 등록 신청의 원본 (BE-020). 신청마다 새 행을 만들어, 반려 뒤 재신청해도 반려 이력이 남는다
 * 다른 서비스(상품)는 SellerChangedEvent로 받은 공개 정보만 복제한다. 반려 사유·심사한 관리자는 여기에만 둔다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "MEMBER_SELLER",
	// 회원의 대기·승인 신청 확인, 가장 최근 신청 조회
	indexes = @Index(name = "idx_member_seller_member", columnList = "member_id, status")
)
public class Seller extends BaseEntity {
	public static final String WITHDRAWN_REJECT_REASON = "회원 탈퇴";

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "seller_id")
	private long id;

	// 신청한 회원. 반려 뒤 재신청하면 한 회원에 행이 여러 개다
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Enumerated(STRING)
	@Column(name = "seller_type", nullable = false, columnDefinition = "varchar(20)")
	private SellerType sellerType;

	// 상호·사업자등록번호는 사업자만 있다. 길이는 상품 복제본(ProductSeller) 칸과 같다
	@Column(name = "business_name", length = 100)
	private String businessName;

	@Column(name = "business_number", length = 20)
	private String businessNumber;

	// 사업자는 대표자, 비사업자는 성명
	@Column(name = "representative_name", nullable = false, length = 50)
	private String representativeName;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private SellerStatus status;

	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;

	// 승인·반려 시각. 심사 전에는 null
	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;

	// 반려 사유. 신청자에게만 보여 주고 이벤트에는 넣지 않는다
	@Column(name = "reject_reason", length = 500)
	private String rejectReason;

	// 심사한 관리자의 회원 ID. 기록용이라 외래 키를 걸지 않는다
	@Column(name = "reviewed_by")
	private Long reviewedBy;

	// 신청자가 승인·반려 결과를 처음 본 시각. 아직 안 봤으면 null. 화면 표시용이라 이벤트에는 넣지 않는다
	@Column(name = "result_checked_at")
	private LocalDateTime resultCheckedAt;

	// 비사업자는 상호·사업자등록번호를 보내도 버린다 (BE-020 결정 4)
	public Seller(
		Member member,
		SellerType sellerType,
		String businessName,
		String businessNumber,
		String representativeName,
		LocalDateTime requestedAt
	) {
		boolean business = sellerType == SellerType.BUSINESS;
		this.member = member;
		this.sellerType = sellerType;
		this.businessName = business ? businessName : null;
		this.businessNumber = business ? formatBusinessNumber(businessNumber) : null;
		this.representativeName = representativeName;
		this.status = SellerStatus.PENDING;
		this.requestedAt = requestedAt;
	}

	// 하이픈 유무와 상관없이 123-45-67890 모양으로 저장한다 (형식 검사는 요청 DTO에서 끝남)
	private static String formatBusinessNumber(String businessNumber) {
		String digits = businessNumber.replace("-", "");
		return digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
	}

	// 대기(PENDING)인 신청만 승인할 수 있다 (BE-020 결정 5). 이미 저장된 신청이라 ID가 있어 바로 발행한다
	public void approve(long adminId, LocalDateTime now) {
		review(adminId, now);
		this.status = SellerStatus.APPROVED;
		publishChangedEvent();
	}

	public void reject(long adminId, String reason, LocalDateTime now) {
		review(adminId, now);
		this.status = SellerStatus.REJECTED;
		this.rejectReason = reason;
		publishChangedEvent();
	}

	// 신청자가 탈퇴하면 대기 중 신청을 반려한다. 심사한 관리자는 없어 reviewedBy는 비워 둔다 (PRO-217)
	// 반려 이벤트로 다른 서비스 사본도 반려 상태가 된다
	public void rejectByWithdrawal(LocalDateTime now) {
		if (status != SellerStatus.PENDING) {
			throw new SellerAlreadyReviewedException("이미 심사한 판매자 신청입니다.");
		}
		this.reviewedAt = now;
		this.status = SellerStatus.REJECTED;
		this.rejectReason = WITHDRAWN_REJECT_REASON;
		publishChangedEvent();
	}

	// 심사가 끝난 신청만 결과를 봤다고 남긴다. 다시 불러도 처음 본 시각을 그대로 둔다
	public void checkResult(LocalDateTime now) {
		if (status == SellerStatus.PENDING) {
			throw new SellerNotReviewedException("아직 심사 중인 판매자 신청입니다.");
		}
		if (resultCheckedAt == null) {
			this.resultCheckedAt = now;
		}
	}

	private void review(long adminId, LocalDateTime now) {
		if (status != SellerStatus.PENDING) {
			throw new SellerAlreadyReviewedException("이미 심사한 판매자 신청입니다.");
		}
		this.reviewedAt = now;
		this.reviewedBy = adminId;
	}

	// 판매자 변경 이벤트를 발행한다. 신청은 ID가 저장할 때(IDENTITY) 정해지므로 save() 뒤에 부른다
	public void publishChangedEvent() {
		publishEvent(new SellerChangedEvent(
			id,
			member.getId(),
			sellerType,
			businessName,
			businessNumber,
			representativeName,
			status,
			requestedAt,
			reviewedAt
		));
	}

	@Override
	public LocalDateTime getCreateDate() {
		return requestedAt;
	}

	// 심사 전에는 신청 시각이 마지막으로 바뀐 시각이다
	@Override
	public LocalDateTime getModifyDate() {
		return reviewedAt != null ? reviewedAt : requestedAt;
	}
}
