package com.bukang.member.out;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.member.domain.Terms;
import com.bukang.member.domain.TermsType;

public interface TermsRepository extends JpaRepository<Terms, Long> {
	boolean existsByTypeAndVersion(TermsType type, String version);

	// 시행일이 지난 약관을 최근 순으로 가져온다. 시행일이 같으면 나중에 만든 것이 먼저 온다
	List<Terms> findByEffectiveDateLessThanEqualOrderByEffectiveDateDescIdDesc(LocalDate today);

	// 종류마다 지금 버전(시행일이 오늘 이전인 것 중 가장 최근) 하나씩
	default List<Terms> findCurrent(LocalDate today) {
		Map<TermsType, Terms> currentByType = new LinkedHashMap<>();
		for (Terms terms : findByEffectiveDateLessThanEqualOrderByEffectiveDateDescIdDesc(today)) {
			currentByType.putIfAbsent(terms.getType(), terms);
		}
		return List.copyOf(currentByType.values());
	}
}
