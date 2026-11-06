package com.bukang.member.in;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.app.SellerFacade;
import com.bukang.member.dto.MarketingUnsubscribeLinkDto;
import com.bukang.member.dto.MemberAdminDto;
import com.bukang.member.dto.MemberNicknameDto;
import com.bukang.member.dto.MemberSellerDto;
import com.bukang.member.dto.SellerPayoutAccountDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 다른 서비스가 동기 호출로 부르는 내부 API. /internal/** 은 게이트웨이에 노출하지 않는다 (order-service와 같은 방식)
 * 회원이 아니라 서비스가 부르므로 회원 ID 헤더를 받지 않는다
 */
@Tag(name = "MemberInternal",
	description = "회원의 판매자 ID 조회, 관리자 여부 조회, 판매자 정산 계좌 등록 여부 조회, 광고 수신 거부 링크 발급, "
		+ "닉네임 여러 명 조회 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/member")
public class InternalV1MemberController {
	private final SellerFacade sellerFacade;
	private final MemberFacade memberFacade;

	@GetMapping("/members/{memberId}/seller")
	@Operation(summary = "회원의 판매자 ID 조회",
		description = "정산 서비스가 판매자 정산 조회 때 회원 ID(X-Member-Id)를 판매자 ID(판매자 신청서 ID)로 바꾸려고 부른다. "
			+ "회원의 가장 최근 판매자 신청의 ID와 상태를 돌려주고, 신청한 적이 없으면 두 칸 모두 null이다. "
			+ "승인 여부는 부르는 쪽이 status로 판단한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (신청이 없으면 sellerId·status가 null)",
			content = @Content(examples = {
				@ExampleObject(name = "승인된 판매자", value = MemberInternalApiExamples.SELLER_APPROVED),
				@ExampleObject(name = "대기 중인 신청", value = MemberInternalApiExamples.SELLER_PENDING),
				@ExampleObject(name = "신청 없음", value = MemberInternalApiExamples.SELLER_NONE)
			})),
		@ApiResponse(responseCode = "404", description = "존재하지 않거나 탈퇴한 회원",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberInternalApiExamples.MEMBER_NOT_FOUND)))
	})
	public ResponseEntity<RsData<MemberSellerDto>> findSellerOfMember(
		@Parameter(description = "회원 ID", example = "7") @PathVariable long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "회원의 판매자 신청을 조회했습니다.", sellerFacade.findSellerOfMember(memberId)));
	}

	@GetMapping("/members/{memberId}/admin")
	@Operation(summary = "관리자 여부 조회",
		description = "정산 서비스가 관리자 API(지급보류 걸기·풀기, 회수 완료)를 받을 때 부른다. "
			+ "회원 역할이 관리자면 admin=true, 일반 회원이면 false를 돌려준다. 관리자가 아니어도 200이며, 403은 부른 서비스가 정한다. "
			+ "탈퇴한 회원은 없는 회원으로 본다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (일반 회원이어도 200)",
			content = @Content(examples = {
				@ExampleObject(name = "관리자", value = MemberInternalApiExamples.ADMIN),
				@ExampleObject(name = "일반 회원", value = MemberInternalApiExamples.NOT_ADMIN)
			})),
		@ApiResponse(responseCode = "404", description = "존재하지 않거나 탈퇴한 회원",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberInternalApiExamples.MEMBER_NOT_FOUND)))
	})
	public ResponseEntity<RsData<MemberAdminDto>> checkAdmin(
		@Parameter(description = "확인할 회원 ID (관리자 API를 부른 회원)", example = "1")
		@PathVariable long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "관리자 여부를 조회했습니다.", memberFacade.checkAdmin(memberId)));
	}

	@GetMapping("/sellers/{sellerId}/payout-account")
	@Operation(summary = "판매자 정산 계좌 등록 여부 조회",
		description = "정산 서비스가 계좌 미등록 판매자의 정산을 보류하려고 부른다. 판매자 신청 때 계좌실명조회를 통과한 "
			+ "정산 계좌가 있으면 registered=true, 없으면(이 기능 전에 신청한 판매자) false를 돌려준다. 계좌번호는 돌려주지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (계좌가 없어도 200)",
			content = @Content(examples = {
				@ExampleObject(name = "계좌 등록됨", value = MemberInternalApiExamples.PAYOUT_ACCOUNT_REGISTERED),
				@ExampleObject(name = "계좌 없음", value = MemberInternalApiExamples.PAYOUT_ACCOUNT_NOT_REGISTERED)
			})),
		@ApiResponse(responseCode = "404", description = "존재하지 않는 판매자 신청",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberInternalApiExamples.SELLER_NOT_FOUND)))
	})
	public ResponseEntity<RsData<SellerPayoutAccountDto>> findPayoutAccount(
		@Parameter(description = "판매자 ID (판매자 신청서 ID)", example = "3") @PathVariable long sellerId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "판매자 정산 계좌 등록 여부를 조회했습니다.",
				sellerFacade.findPayoutAccount(sellerId)));
	}

	// CRM이 광고 메시지를 만들 때 회원마다 부른다. 토큰을 만드는 서명 키는 회원 서비스에만 있다 (BUK-75, BE-039)
	@GetMapping("/members/{memberId}/marketing-unsubscribe-link")
	@Operation(summary = "광고 수신 거부 링크 발급",
		description = "CRM 서비스가 광고 메시지 끝에 붙일 수신 거부 링크를 받아 간다. 링크는 프론트 확인 화면(/unsubscribe?token=...)이고, "
			+ "토큰은 발급 뒤 1년 동안 쓸 수 있다. 저장하지 않으므로 부를 때마다 새 토큰이 나온다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "발급 성공",
			content = @Content(examples = @ExampleObject(value = MemberInternalApiExamples.UNSUBSCRIBE_LINK))),
		@ApiResponse(responseCode = "404", description = "존재하지 않거나 탈퇴한 회원",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberInternalApiExamples.MEMBER_NOT_FOUND)))
	})
	public ResponseEntity<RsData<MarketingUnsubscribeLinkDto>> issueUnsubscribeLink(
		@Parameter(description = "광고를 받을 회원 ID", example = "7") @PathVariable long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "수신 거부 링크를 발급했습니다.", memberFacade.issueUnsubscribeLink(memberId)));
	}

	// 주문 서비스가 파티원 목록에 이름을 띄울 때 부른다 (BUK-39). 닉네임 말고 다른 개인정보는 내보내지 않는다
	@GetMapping("/members/nicknames")
	@Operation(summary = "회원 닉네임 여러 명 조회",
		description = "회원 ID 목록(쉼표로 구분)을 받아 회원마다 memberId와 nickname을 돌려준다. "
			+ "같은 ID는 한 번만 돌려주고, 없는 회원 ID는 결과에서 뺀다. 탈퇴한 회원은 닉네임을 「탈퇴한 회원」으로 돌려준다. "
			+ "ids가 비어 있으면 빈 목록이다. 돌려주는 순서는 정해져 있지 않다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없는 회원 ID는 빠짐)",
			content = @Content(examples = @ExampleObject(value = MemberInternalApiExamples.NICKNAMES))),
		@ApiResponse(responseCode = "400", description = "서로 다른 회원 ID가 100개를 넘음 또는 회원 ID가 숫자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "100개 초과", value = MemberInternalApiExamples.NICKNAMES_TOO_MANY),
				@ExampleObject(name = "숫자가 아님", value = MemberInternalApiExamples.INVALID_ID_FORMAT)
			}))
	})
	public ResponseEntity<RsData<List<MemberNicknameDto>>> findNicknames(
		@Parameter(description = "회원 ID 목록 (쉼표로 구분, 서로 다른 ID 최대 100개)", example = "1,2,3")
		@RequestParam(defaultValue = "") List<Long> ids
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "회원 닉네임을 조회했습니다.", memberFacade.findNicknames(ids)));
	}
}
