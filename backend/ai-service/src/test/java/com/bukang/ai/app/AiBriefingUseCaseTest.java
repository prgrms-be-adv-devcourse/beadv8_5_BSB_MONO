package com.bukang.ai.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.ai.domain.AiScreen;
import com.bukang.ai.domain.exception.AiForbiddenException;
import com.bukang.ai.dto.AiBriefingDto;
import com.bukang.ai.dto.AiBriefingDto.Metric;
import com.bukang.ai.dto.AiBriefingDto.Todo;
import com.bukang.ai.out.ops.OpsPort;
import com.bukang.ai.support.StubMemberRolePort;
import com.bukang.ai.support.StubOpsPort;

// 브리핑 문장은 숫자로 정해진다. 조회 하나가 실패해도 다른 칸은 그대로 나온다
class AiBriefingUseCaseTest {
	private static final long SELLER = 3L;
	private static final long ADMIN = 20L;

	private final StubOpsPort opsPort = new StubOpsPort();
	private final AiBriefingUseCase useCase =
		new AiBriefingUseCase(new StubMemberRolePort(Set.of(SELLER), Set.of(ADMIN)), opsPort);

	@Test
	@DisplayName("판매자: 보낼 키트와 지급보류를 할 일로, 다음 정산을 지표로 만든다")
	void seller() {
		opsPort.kitSummary = new OpsPort.KitSummary(6, 2);
		opsPort.payoutSummary = new OpsPort.PayoutSummary(LocalDate.of(2026, 11, 20), 1250000, 1);

		AiBriefingDto briefing = useCase.briefing(SELLER, AiScreen.SELLER);

		assertThat(briefing.headline()).isEqualTo("송장 입력 대기 키트가 4건 있어요. 지급보류 1건의 사유를 확인해 주세요.");
		assertThat(briefing.basis()).matches("(오전|오후) \\d{1,2}:\\d{2} 기준");
		assertThat(briefing.todos()).extracting(Todo::title, Todo::description, Todo::href).containsExactly(
			tuple("송장 입력 대기 4건", "환불 기한이 지나면 2건을 더 보낼 수 있어요", "/seller/kits"),
			tuple("지급보류 1건", "보류가 풀려야 그 정산이 지급돼요",
				"/seller/settlements?tab=holds"));
		assertThat(briefing.metrics()).containsExactly(
			new Metric("보낼 키트", "4건", "환불 기한 전 2건", null),
			new Metric("다음 정산 · 11/20 지급", "₩1,250,000", "보류 1건 빼고", null));
		assertThat(opsPort.calledMemberIds).containsOnly(SELLER);
	}

	@Test
	@DisplayName("판매자: 지금 보낼 키트가 없고 정산 예정도 없으면 할 일이 없다고 말한다")
	void sellerNothing() {
		AiBriefingDto briefing = useCase.briefing(SELLER, AiScreen.SELLER);

		assertThat(briefing.headline()).isEqualTo("오늘 먼저 처리할 일이 없어요.");
		assertThat(briefing.todos()).isEmpty();
		assertThat(briefing.metrics()).extracting(Metric::label, Metric::value).containsExactly(
			tuple("보낼 키트", "0건"), tuple("다음 정산", "-"));
	}

	@Test
	@DisplayName("판매자: 환불 기한 전 키트만 있으면 기한 뒤에 보낼 수 있다고 할 일에 넣는다")
	void sellerOnlyBeforeRefundDeadline() {
		opsPort.kitSummary = new OpsPort.KitSummary(3, 3);

		AiBriefingDto briefing = useCase.briefing(SELLER, AiScreen.SELLER);

		assertThat(briefing.headline()).isEqualTo("오늘 먼저 처리할 일이 없어요.");
		assertThat(briefing.todos()).extracting(Todo::title).containsExactly("환불 기한 뒤 보낼 키트 3건");
	}

	@Test
	@DisplayName("키트 조회만 실패하면 그 칸만 「불러오지 못했어요」이고 정산 칸은 그대로다")
	void sellerKitsFailed() {
		StubOpsPort failingKits = new StubOpsPort() {
			@Override
			public KitSummary kitSummary(long memberId) {
				throw new IllegalStateException("연결 실패");
			}
		};
		failingKits.payoutSummary = new OpsPort.PayoutSummary(LocalDate.of(2026, 11, 20), 500000, 2);
		AiBriefingUseCase failing =
			new AiBriefingUseCase(new StubMemberRolePort(Set.of(SELLER), Set.of()), failingKits);

		AiBriefingDto briefing = failing.briefing(SELLER, AiScreen.SELLER);

		assertThat(briefing.headline()).isEqualTo("지급보류 2건의 사유를 확인해 주세요.");
		assertThat(briefing.todos()).extracting(Todo::title, Todo::description).containsExactly(
			tuple("송장 입력 대기", AiBriefingUseCase.FAILED),
			tuple("지급보류 2건", "보류가 풀려야 그 정산이 지급돼요"));
		assertThat(briefing.metrics()).extracting(Metric::value).containsExactly("-", "₩500,000");
	}

	@Test
	@DisplayName("모든 조회가 실패해도 예외 없이 화면에서 확인하라는 브리핑을 준다")
	void allFailed() {
		opsPort.unavailable = true;

		AiBriefingDto briefing = useCase.briefing(ADMIN, AiScreen.ADMIN);

		assertThat(briefing.headline()).isEqualTo("일부 숫자를 불러오지 못했어요. 아래 화면에서 확인해 주세요.");
		assertThat(briefing.todos()).extracting(Todo::description).containsOnly(AiBriefingUseCase.FAILED);
		assertThat(briefing.metrics()).extracting(Metric::value).containsOnly("-");
	}

	@Test
	@DisplayName("관리자: 판매자 승인 대기와 관리자 담당 문의를 할 일로 만든다")
	void admin() {
		LocalDate today = LocalDate.now(AiToolTrace.ZONE);
		opsPort.pendingRequestedAts = List.of(today.minusDays(4).atTime(10, 0), today.atTime(9, 0));
		opsPort.waitingInquiries = 7;
		opsPort.adminWaitingInquiries = 2;

		AiBriefingDto briefing = useCase.briefing(ADMIN, AiScreen.ADMIN);

		assertThat(briefing.headline()).isEqualTo("판매자 승인 대기가 2건 있어요. 관리자가 답할 문의가 2건 있어요.");
		assertThat(briefing.todos()).extracting(Todo::title, Todo::href).containsExactly(
			tuple("판매자 승인 대기 2건", "/admin/sellers"),
			tuple("답변 대기 문의 · 관리자 담당 2건", "/admin/inquiries"));
		assertThat(briefing.metrics()).containsExactly(
			new Metric("판매자 승인 대기", "2건", "가장 오래된 신청 5일째", null),
			new Metric("답변 대기 문의 · 전체", "7건", "관리자 담당 2건", null));
	}

	@Test
	@DisplayName("관리자: 판매자 담당 문의만 남았으면 할 일에 넣지 않는다")
	void adminOnlySellerInquiries() {
		opsPort.waitingInquiries = 4;
		opsPort.pendingRequestedAts = List.<LocalDateTime>of();

		AiBriefingDto briefing = useCase.briefing(ADMIN, AiScreen.ADMIN);

		assertThat(briefing.headline()).isEqualTo("오늘 먼저 처리할 일이 없어요.");
		assertThat(briefing.todos()).isEmpty();
	}

	@Test
	@DisplayName("그 화면의 역할이 없으면 조회하지 않고 막는다")
	void forbidden() {
		assertThatThrownBy(() -> useCase.briefing(SELLER, AiScreen.ADMIN)).isInstanceOf(AiForbiddenException.class);
		assertThatThrownBy(() -> useCase.briefing(ADMIN, AiScreen.SELLER)).isInstanceOf(AiForbiddenException.class);
		assertThat(opsPort.calledMemberIds).isEmpty();
	}
}
