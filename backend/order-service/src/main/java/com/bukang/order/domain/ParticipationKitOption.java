package com.bukang.order.domain;

import static lombok.AccessLevel.PROTECTED;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 참가 신청이 결제 때 고른 키트 옵션의 사본 (11/07 호빈님 제안 표). 예: 이름 "T-shirt" · 값 "L"
 * 상품에서 옵션 이름·값이 바뀌어도 신청 기록은 그대로 둔다. 옵션 이름마다 하나만 고른다 (유니크)
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "ORDER_PARTICIPATION_KIT_OPTION",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_participation_kit_option_name", columnNames = {"participation_id", "option_name"})
	}
)
public class ParticipationKitOption extends BaseIdAndTime {
	@Column(name = "participation_id", nullable = false)
	private long participationId;

	@Column(name = "kit_option_id", nullable = false)
	private long kitOptionId;

	@Column(name = "option_name", nullable = false, length = 50)
	private String optionName;

	@Column(name = "option_value", nullable = false, length = 50)
	private String optionValue;

	public static ParticipationKitOption of(long participationId, long kitOptionId, String optionName,
		String optionValue) {
		ParticipationKitOption option = new ParticipationKitOption();
		option.participationId = participationId;
		option.kitOptionId = kitOptionId;
		option.optionName = optionName;
		option.optionValue = optionValue;
		return option;
	}
}
