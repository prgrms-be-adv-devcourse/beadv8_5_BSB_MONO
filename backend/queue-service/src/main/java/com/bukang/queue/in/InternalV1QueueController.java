package com.bukang.queue.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.queue.app.QueueFacade;
import com.bukang.queue.dto.QueueAdmissionDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * 서비스끼리 부르는 내부 API. 게이트웨이에 라우팅하지 않는다(/internal/** 경로)
 * 주문 서비스가 회원이 대회·종목에 입장(active) 중인지와 입장 끝 시각을 묻는다 (주문 QueueAdmissionPort).
 * 입장 중이 아니어도, 대기열(Redis)에 연결하지 못해도 오류가 아니라 200과 admitted=false로 돌려준다.
 */
@Tag(name = "QueueAdmission", description = "주문 서비스용 대기열 입장 확인 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/queue")
public class InternalV1QueueController {
	private final QueueFacade queueFacade;

	// 에러 응답은 QueueExceptionHandler가 RsData(data: null) 형식으로 반환한다
	@GetMapping("/races/{raceId}/racecourses/{raceCourseId}")
	@Operation(summary = "대기열 입장 확인",
		description = "회원이 대회·종목에 입장(active) 중인지와 입장 끝 시각, 끝 시각까지 남은 시간(ms)을 돌려준다. "
			+ "입장 끝 시각이 주문의 자리 확보 기한·결제 기한이 된다. "
			+ "줄에서 기다리는 중, 입장 시간이 끝남, 대기열에 없음이면 admitted=false다. "
			+ "대기열(Redis)에 연결하지 못해도 오류가 아니라 admitted=false로 돌려준다. "
			+ "대기열(Redis)만 읽고 대회·종목이 있는지는 확인하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "확인 성공 (입장 중 또는 입장 중 아님)",
			content = @Content(examples = {
				@ExampleObject(name = "입장 중", value = QueueAdmissionApiExamples.ADMITTED),
				@ExampleObject(name = "입장 중 아님", value = QueueAdmissionApiExamples.NOT_ADMITTED)
			})),
		@ApiResponse(responseCode = "400", description = "대회·종목 ID 또는 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "ID 형식 오류", value = QueueApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "대회·종목 ID가 0 이하", value = QueueApiExamples.INVALID_RACE_ID),
				@ExampleObject(name = "회원 ID가 0 이하", value = QueueApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = QueueApiExamples.LOGIN_REQUIRED)))
	})
	public ResponseEntity<RsData<QueueAdmissionDto>> getAdmission(
		@LoginMemberId long memberId,
		@Parameter(description = "대회 ID", example = "1")
		@PathVariable @Positive(message = "대회 ID는 0보다 커야 합니다.") long raceId,
		@Parameter(description = "종목 ID", example = "1")
		@PathVariable @Positive(message = "종목 ID는 0보다 커야 합니다.") long raceCourseId
	) {
		QueueAdmissionDto admission = queueFacade.getAdmission(memberId, raceId, raceCourseId);
		String message = admission.isAdmitted() ? "대기열에 입장 중입니다." : "대기열에 입장 중이 아닙니다.";
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, message, admission));
	}
}
