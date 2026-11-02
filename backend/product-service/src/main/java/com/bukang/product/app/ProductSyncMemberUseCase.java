package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bukang.product.domain.ProductMember;
import com.bukang.product.out.ProductMemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSyncMemberUseCase {
	private final ProductMemberRepository productMemberRepository;

	// 원본 값으로 덮어쓴다 (ID가 있으면 UPDATE, 없으면 INSERT). 같은 메시지가 다시 와도 결과가 같다
	// 가입·변경은 토픽이 달라 받는 순서가 바뀔 수 있다. 저장된 수정 시각보다 오래된 메시지는 건너뛰어,
	// 변경 뒤에 늦게 온 가입 메시지가 닉네임을 되돌리지 않게 한다. 그 뒤의 변경은 수정 시각이 더 늦어 그대로 반영된다
	public ProductMember syncMember(long id, LocalDateTime createDate, LocalDateTime modifyDate, String nickname) {
		Optional<ProductMember> stored = productMemberRepository.findById(id);
		if (stored.isPresent() && modifyDate.isBefore(stored.get().getModifyDate())) {
			log.info("저장된 값보다 오래된 회원 메시지를 건너뜁니다. memberId={}, modifyDate={}, storedModifyDate={}",
				id, modifyDate, stored.get().getModifyDate());
			return stored.get();
		}

		return productMemberRepository.save(new ProductMember(id, createDate, modifyDate, nickname));
	}
}
