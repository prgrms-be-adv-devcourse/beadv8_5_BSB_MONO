package com.bukang.member.crew.app;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.bukang.member.crew.domain.CrewPenalty;
import com.bukang.member.crew.domain.exception.CrewPenaltyLookupFailedException;
import com.bukang.member.crew.out.CrewPenaltyRepository;
import com.bukang.member.out.product.LowPaymentCrew;
import com.bukang.member.out.product.LowPaymentCrewPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제율 미달 크루의 파티 만들기 제한을 저장한다 (BUK-187). 매일 CrewPenaltyScheduler가 부른다
 * 한국 시간 기준 이번 달과 지난달을 상품 서비스에 묻는다. 지난달은 월말에 늦게 확정된 미달을 놓치지 않으려고 다시 묻는다
 * 한 달 조회가 실패하면 그 달만 건너뛰고 경고 로그를 남긴다. 다음 날 다시 묻는다
 * 메서드에 트랜잭션을 두지 않아 크루마다 따로 저장된다. 한 크루가 실패해도 다른 크루 저장은 남는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrewPenaltyRecordUseCase {
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private final LowPaymentCrewPort lowPaymentCrewPort;
	private final CrewPenaltyRepository crewPenaltyRepository;

	// 새로 저장한 제한 수를 돌려준다
	public int record(Instant now) {
		YearMonth thisMonth = YearMonth.from(now.atZone(KOREA));
		// 다른 크루 시각(CrewFacade.now())과 같은 방식(서버 시간대)으로 적어야 제한 확인과 맞는다
		LocalDateTime startAt = LocalDateTime.ofInstant(now, ZoneId.systemDefault());
		return recordMonth(thisMonth, startAt) + recordMonth(thisMonth.minusMonths(1), startAt);
	}

	private int recordMonth(YearMonth month, LocalDateTime startAt) {
		List<LowPaymentCrew> crews;
		try {
			crews = lowPaymentCrewPort.findLowPaymentCrews(month);
		} catch (CrewPenaltyLookupFailedException exception) {
			log.warn("결제율 미달 크루를 묻지 못해 이 달은 건너뜁니다. 내일 다시 묻습니다. {}", exception.getMessage());
			return 0;
		}
		int saved = 0;
		for (LowPaymentCrew crew : crews) {
			if (save(crew, month, startAt)) {
				saved++;
			}
		}
		return saved;
	}

	private boolean save(LowPaymentCrew crew, YearMonth month, LocalDateTime startAt) {
		if (crew.crewId() == null || crew.lowPaymentCount() == null) {
			log.warn("결제율 미달 크루 응답에 값이 빠져 건너뜁니다. month={}, crew={}", month, crew);
			return false;
		}
		// 이미 있는 (크루, 달)은 다시 저장하지 않는다. 다시 저장하면 제한 시작일이 밀린다
		if (crewPenaltyRepository.existsByCrewIdAndPenaltyMonth(crew.crewId(), month.toString())) {
			return false;
		}
		try {
			crewPenaltyRepository.save(CrewPenalty.start(crew.crewId(), month, crew.lowPaymentCount(), startAt));
			return true;
		} catch (DataIntegrityViolationException exception) {
			// 다른 서버가 확인과 저장 사이에 먼저 저장했다. 먼저 저장된 행을 그대로 둔다
			log.info("이미 저장된 크루 제한이라 건너뜁니다. crewId={}, month={}", crew.crewId(), month);
			return false;
		}
	}
}
