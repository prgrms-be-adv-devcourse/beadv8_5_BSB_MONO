package com.bukang.product.app;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.bukang.product.domain.CrewLowPaymentPolicy;
import com.bukang.product.dto.CrewLowPaymentDto;
import com.bukang.product.out.CrewPartyPaymentRow;
import com.bukang.product.out.ParticipationHoldRepository;

import lombok.RequiredArgsConstructor;

/**
 * 한 달 동안 결제율 미달이 기준 횟수 이상인 크루를 찾는다 (BUK-176)
 * 회원 서비스가 매일 불러 크루 참가 제한을 기록하는 것을 가정한다. 결과는 저장하지 않고 부를 때마다 센다.
 * 달이 끝나기 전에 불러도 되고, 그때까지 결제 기한이 끝난 파티로 센다
 */
@Service
@RequiredArgsConstructor
public class CrewLowPaymentQueryUseCase {
	private final ParticipationHoldRepository participationHoldRepository;

	// month: 자리를 확보한 달. 그 달 1일 0시부터 다음 달 1일 0시 전까지 확보한 파티를 센다
	public List<CrewLowPaymentDto> find(YearMonth month) {
		LocalDateTime from = month.atDay(1).atStartOfDay();
		LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();

		// 크루 ID 순으로 응답하려고 TreeMap에 모은다
		Map<Long, List<CrewLowPaymentDto.Party>> lowPartiesByCrew = new TreeMap<>();
		for (CrewPartyPaymentRow row : participationHoldRepository.findFinishedCrewPartyPayments(from, to)) {
			if (CrewLowPaymentPolicy.isLow(row.getPaidCount(), row.getHeldCount())) {
				lowPartiesByCrew.computeIfAbsent(row.getCrewId(), crewId -> new ArrayList<>())
					.add(new CrewLowPaymentDto.Party(row.getPartyId(), row.getHeldCount(), row.getPaidCount()));
			}
		}

		return lowPartiesByCrew.entrySet().stream()
			.filter(entry -> CrewLowPaymentPolicy.needsPenalty(entry.getValue().size()))
			.map(entry -> new CrewLowPaymentDto(entry.getKey(), entry.getValue().size(),
				entry.getValue().stream().sorted(Comparator.comparingLong(CrewLowPaymentDto.Party::partyId)).toList()))
			.toList();
	}
}
