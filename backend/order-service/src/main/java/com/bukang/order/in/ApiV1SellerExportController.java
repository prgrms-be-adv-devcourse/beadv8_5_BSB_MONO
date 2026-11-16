package com.bukang.order.in;

import java.nio.charset.StandardCharsets;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.order.app.SellerExportFile;
import com.bukang.order.app.SellerOrderFacade;
import com.bukang.order.dto.SellerKitExportRequestDto;
import com.bukang.order.dto.SellerRosterExportRequestDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 판매자 참가자 명단·키트 배송 목록 내려받기 (시안 S03·S08, BUK-228). 로그인한 회원이 승인된 판매자일 때만 쓴다
// 성공하면 RsData가 아니라 CSV 파일(text/csv, UTF-8 BOM)을 그대로 준다. 에러 응답은 RsData(data: null) 형식이다
// 내려받기마다 사유와 함께 기록이 남으므로(부수효과) GET이 아니라 POST다
@Tag(name = "SellerExport", description = "판매자 목록 내려받기 API (참가자 명단, 키트 배송 목록). 사유를 받아 기록한다")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order/seller")
public class ApiV1SellerExportController {
	private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final SellerOrderFacade sellerOrderFacade;

	@PostMapping("/orders/roster-export")
	@Operation(summary = "참가자 명단 내려받기 (CSV)",
		description = "대회 당일 확인용 참가자 명단. 결제 완료(참가 확정)인 참가권만 종목·주문 순으로 담는다. "
			+ "칸: 주문번호, 종목, 참가자 이름, 연락처, 키트 옵션, 파티 번호. 엑셀에서 바로 열리는 CSV(UTF-8 BOM)다. "
			+ "내려받을 때마다 누가 언제 왜 몇 줄 받았는지 기록한다. 다른 판매자의 대회를 고르면 머리줄만 있는 파일이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "CSV 파일",
			content = @Content(mediaType = "text/csv",
				examples = @ExampleObject(value = SellerExportApiExamples.ROSTER_CSV))),
		@ApiResponse(responseCode = "400", description = "대회·사유 없음, 사유가 김, 형식·회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "대회 없음", value = SellerExportApiExamples.MISSING_RACE),
				@ExampleObject(name = "사유 없음", value = SellerExportApiExamples.MISSING_REASON),
				@ExampleObject(name = "사유가 200자 넘음", value = SellerExportApiExamples.REASON_TOO_LONG),
				@ExampleObject(name = "숫자 아닌 값", value = SellerExportApiExamples.INVALID_FORMAT),
				@ExampleObject(name = "회원 ID가 0 이하", value = SellerExportApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerExportApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerExportApiExamples.NOT_SELLER))),
		@ApiResponse(responseCode = "503", description = "판매자 확인을 아직 쓸 수 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerExportApiExamples.SELLER_UNAVAILABLE)))
	})
	public ResponseEntity<byte[]> exportRoster(
		@LoginMemberId long memberId,
		@Valid @RequestBody SellerRosterExportRequestDto request
	) {
		return csv(sellerOrderFacade.exportRoster(memberId, request));
	}

	@PostMapping("/kits/export")
	@Operation(summary = "키트 배송 목록 내려받기 (CSV)",
		description = "키트 배송 관리 목록과 같은 조건(대회·배송 상태)으로 모두 담는다. "
			+ "칸: 주문번호, 종목, 참가권 상태, 참가자 이름, 받는 분, 받는 분 연락처, 우편번호, 주소, 상세 주소, 키트 옵션, "
			+ "배송 상태, 택배사, 운송장 번호. 엑셀에서 바로 열리는 CSV(UTF-8 BOM)다. 내려받을 때마다 사유와 함께 기록한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "CSV 파일",
			content = @Content(mediaType = "text/csv",
				examples = @ExampleObject(value = SellerExportApiExamples.KIT_CSV))),
		@ApiResponse(responseCode = "400", description = "사유 없음, 사유가 김, 없는 배송 상태, 회원 ID 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "사유 없음", value = SellerExportApiExamples.MISSING_REASON),
				@ExampleObject(name = "사유가 200자 넘음", value = SellerExportApiExamples.REASON_TOO_LONG),
				@ExampleObject(name = "없는 배송 상태·숫자 아닌 값", value = SellerExportApiExamples.INVALID_FORMAT),
				@ExampleObject(name = "회원 ID가 0 이하", value = SellerExportApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerExportApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerExportApiExamples.NOT_SELLER))),
		@ApiResponse(responseCode = "503", description = "판매자 확인을 아직 쓸 수 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerExportApiExamples.SELLER_UNAVAILABLE)))
	})
	public ResponseEntity<byte[]> exportKitShipments(
		@LoginMemberId long memberId,
		@Valid @RequestBody SellerKitExportRequestDto request
	) {
		return csv(sellerOrderFacade.exportKitShipments(memberId, request));
	}

	private static ResponseEntity<byte[]> csv(SellerExportFile file) {
		// 개인정보 파일이라 브라우저·중간 서버가 저장하지 않게 한다
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.contentType(TEXT_CSV)
			.header(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment().filename(file.fileName()).build().toString())
			.body(file.content());
	}
}
