package com.bukang.member.app;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.domain.Member;
import com.bukang.member.domain.MemberStatus;
import com.bukang.member.dto.IdentityReviewGroupDto;
import com.bukang.member.dto.IdentityReviewMemberDto;
import com.bukang.member.dto.IdentityReviewPageDto;
import com.bukang.member.out.MemberRepository;

import lombok.RequiredArgsConstructor;

// 어드민 「확인이 필요한 계정」: 이름 + 생년월일 해시가 같고 휴대폰이 다른 회원 묶음 (BUK-72, BE-038)
// 카카오 인증은 CI가 없어 같은 사람이 휴대폰만 바꿔 가입할 수 있으므로 사람이 보고 판단한다
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IdentityReviewQueryUseCase {
	static final int MAX_PAGE_SIZE = 100;

	private final AdminVerifyUseCase adminVerifyUseCase;
	private final MemberRepository memberRepository;

	public IdentityReviewPageDto find(long adminId, int page, int size) {
		adminVerifyUseCase.verify(adminId);
		// 읽기 전용 관리자 목록이라 범위 밖 값은 거절하지 않고 가까운 값으로 맞춘다
		PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
		Page<String> hashes = memberRepository.findIdentityReviewHashes(pageRequest);

		Map<String, List<Member>> membersByHash = memberRepository
			.findByNameBirthHashInAndStatusNot(hashes.getContent(), MemberStatus.WITHDRAWN).stream()
			.collect(Collectors.groupingBy(Member::getNameBirthHash));
		List<IdentityReviewGroupDto> groups = hashes.getContent().stream()
			.map(hash -> new IdentityReviewGroupDto(membersByHash.getOrDefault(hash, List.of()).stream()
				.sorted(Comparator.comparing(Member::getCreateDate).reversed())
				.map(IdentityReviewMemberDto::from)
				.toList()))
			.toList();

		return new IdentityReviewPageDto(groups, hashes.getNumber(), hashes.getSize(), hashes.getTotalElements(),
			hashes.getTotalPages());
	}
}
