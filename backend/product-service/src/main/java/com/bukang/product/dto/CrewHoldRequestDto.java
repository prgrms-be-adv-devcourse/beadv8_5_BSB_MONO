package com.bukang.product.dto;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 주문 서비스가 파티장의 [대회 참가] 때 보내는 크루 자리 확보 요청 (주문 CrewSeatHoldPort.holdAll)
// 파티장인지는 주문이 확인하고 부르므로 회원 ID는 받지 않는다
@Schema(description = "크루 자리 확보 요청 (주문 서비스가 파티장의 [대회 참가] 때 파티원 수만큼 한꺼번에 보낸다)")
@AllArgsConstructor
@Getter
public class CrewHoldRequestDto {
	// 같은 파티로 다시 요청하면 새 자리를 잡지 않고 처음 결과를 돌려준다 (멱등 키)
	@Schema(description = "주문 서비스의 파티 ID (같은 값으로 다시 요청하면 처음 결과를 돌려준다)", example = "31")
	@NotNull(message = "파티 ID를 작성해주세요.")
	@Positive(message = "파티 ID는 0보다 커야 합니다.")
	private final Long partyId;

	// 자리 표(participation_hold.crew_id)에 남겨 크루 결제율 미달을 셀 때 쓴다
	@Schema(description = "파티의 크루 ID", example = "7")
	@NotNull(message = "크루 ID를 작성해주세요.")
	@Positive(message = "크루 ID는 0보다 커야 합니다.")
	private final Long crewId;

	// 크루 예매 기간인지 보고, 자리 목록의 종목이 모두 이 대회 것인지 확인한다
	@Schema(description = "대회 ID", example = "2")
	@NotNull(message = "대회 ID를 작성해주세요.")
	@Positive(message = "대회 ID는 0보다 커야 합니다.")
	private final Long raceId;

	// 파티원 결제 기한. 이 시각까지만 자리를 유지한다
	@Schema(description = "자리를 잡아 둘 끝 시각 (파티원 결제 기한, 지금보다 늦어야 한다)", example = "2026-11-21T06:00:00")
	@NotNull(message = "자리 끝 시각을 작성해주세요.")
	private final LocalDateTime expiresAt;

	@ArraySchema(arraySchema = @Schema(description = "잡을 자리 목록 (파티원 1명 = 자리 1석)"))
	@NotEmpty(message = "잡을 자리를 하나 이상 작성해주세요.")
	private final List<@NotNull(message = "자리 정보를 작성해주세요.") @Valid CrewHoldSeatDto> seats;
}
