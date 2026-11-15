package com.bukang.member.out.product;

import java.time.YearMonth;
import java.util.List;

/**
 * 상품 서비스에 그 달 결제율 미달 크루를 묻는다 (BUK-176·BUK-187). 미달 판단은 상품 서비스가 한다
 * 묻지 못하면 CrewPenaltyLookupFailedException을 던진다
 */
public interface LowPaymentCrewPort {
	List<LowPaymentCrew> findLowPaymentCrews(YearMonth month);
}
