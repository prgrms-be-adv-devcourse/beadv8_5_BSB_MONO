package com.bukang.member.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.member.domain.Seller;
import com.bukang.member.domain.exception.MemberNotFoundException;
import com.bukang.member.domain.exception.SellerNotFoundException;
import com.bukang.member.dto.MemberSellerDto;
import com.bukang.member.dto.SellerApplyRequestDto;
import com.bukang.member.dto.SellerDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.SellerRepository;

import lombok.RequiredArgsConstructor;

// 판매자 등록 신청과 관리자 심사 (BE-020)
// 신청·승인·반려는 트랜잭션 안에서 이벤트를 발행해야 커밋 뒤 Kafka로 나간다 (MemberKafkaPublisher)
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SellerFacade {
	private final SellerApplyUseCase sellerApplyUseCase;
	private final SellerReviewUseCase sellerReviewUseCase;
	private final SellerResultCheckUseCase sellerResultCheckUseCase;
	private final AdminVerifyUseCase adminVerifyUseCase;
	private final SellerRepository sellerRepository;
	private final MemberRepository memberRepository;

	@Transactional
	public SellerDto apply(long memberId, SellerApplyRequestDto request) {
		return SellerDto.from(sellerApplyUseCase.apply(
			memberId,
			request.getSellerType(),
			request.getBusinessName(),
			request.getBusinessNumber(),
			request.getRepresentativeName(),
			LocalDateTime.now()
		));
	}

	public SellerDto findMine(long memberId) {
		return sellerRepository.findFirstByMemberIdOrderByIdDesc(memberId)
			.map(SellerDto::from)
			.orElseThrow(() -> new SellerNotFoundException("판매자 신청 내역이 없습니다."));
	}

	@Transactional
	public SellerDto checkMyResult(long memberId) {
		return SellerDto.from(sellerResultCheckUseCase.check(memberId, LocalDateTime.now()));
	}

	// 다른 서비스(정산)가 회원 ID로 판매자 ID를 찾는다. 탈퇴했거나 없는 회원은 404, 신청이 없으면 빈 칸으로 돌려준다
	// 대기·승인 신청이 있으면 다시 신청할 수 없으므로(SellerApplyUseCase) 승인된 신청이 있으면 가장 최근 신청이 그것이다
	public MemberSellerDto findSellerOfMember(long memberId) {
		memberRepository.findActiveById(memberId)
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."));
		return sellerRepository.findFirstByMemberIdOrderByIdDesc(memberId)
			.map(MemberSellerDto::from)
			.orElse(MemberSellerDto.NONE);
	}

	// status가 null이면 전체를 최근 신청부터 돌려준다
	public List<SellerDto> findAllForAdmin(long adminId, SellerStatus status) {
		adminVerifyUseCase.verify(adminId);
		List<Seller> sellers = status == null
			? sellerRepository.findAllByOrderByIdDesc()
			: sellerRepository.findAllByStatusOrderByIdDesc(status);
		return sellers.stream().map(SellerDto::from).toList();
	}

	@Transactional
	public SellerDto approve(long adminId, long sellerId) {
		adminVerifyUseCase.verify(adminId);
		return SellerDto.from(sellerReviewUseCase.approve(adminId, sellerId, LocalDateTime.now()));
	}

	@Transactional
	public SellerDto reject(long adminId, long sellerId, String reason) {
		adminVerifyUseCase.verify(adminId);
		return SellerDto.from(sellerReviewUseCase.reject(adminId, sellerId, reason, LocalDateTime.now()));
	}
}
