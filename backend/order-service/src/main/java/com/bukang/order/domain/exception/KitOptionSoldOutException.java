package com.bukang.order.domain.exception;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;
import com.bukang.order.dto.KitOptionChoiceDto;

import lombok.Getter;

// 고른 키트 옵션이 판매 상한(stock)까지 팔림 (11/09 결정). 자리는 결제 기한까지 그대로라 다른 옵션으로 다시 결제한다
// 팔린 옵션은 응답 data.soldOut으로도 준다 (OrderExceptionHandler). 프론트가 그 옵션을 고를 수 없게 바꾼다
// 판매 상한은 구매자에게 보여 주지 않으므로 문구·응답에 수량을 넣지 않는다
@Getter
public class KitOptionSoldOutException extends BusinessException {
	public static final String CODE = "ORDER_KIT_OPTION_SOLD_OUT";

	private final List<KitOptionChoiceDto> soldOut;

	public KitOptionSoldOutException(List<KitOptionChoiceDto> soldOut) {
		super(HttpStatus.CONFLICT, CODE, "선택하신 %s은(는) 준비된 수량이 모두 팔렸어요. 다른 옵션을 골라 주세요.".formatted(
			soldOut.stream().map(option -> option.name() + " " + option.value()).collect(Collectors.joining(", "))));
		this.soldOut = soldOut;
	}
}
