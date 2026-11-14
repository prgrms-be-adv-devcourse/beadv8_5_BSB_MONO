package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.Order;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.exception.ApplicationPeriodException;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.domain.exception.OrderNotFoundException;
import com.bukang.order.domain.exception.ParticipationAccessDeniedException;
import com.bukang.order.domain.exception.ParticipationNotEditableException;
import com.bukang.order.domain.exception.ParticipationNotFoundException;
import com.bukang.order.domain.exception.RaceCourseNotFoundException;
import com.bukang.order.out.OrderItemRepository;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.kit.KitOptionPort;
import com.bukang.order.out.kit.KitOptionPort.KitOption;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제한 참가권의 키트 옵션을 접수 마감 전까지 바꾼다 (BUK-226). 키트는 접수가 끝난 뒤 만들어 보내므로 마감 전에는 바꿀 수 있다
 * 결제와 같은 방식: 상품 HTTP(종목·옵션)와 판매 수 줄 만들기는 잠그기 전에, 잠금·판매 수 옮기기는 한 트랜잭션에서 한다
 * 잠금 순서도 결제·취소와 같다: 주문 → 참가 신청 → 판매 수 줄(옵션 ID 오름차순)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KitOptionChangeUseCase {
	private final ParticipationRepository participationRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final RaceCoursePort raceCoursePort;
	private final KitOptionPort kitOptionPort;
	private final KitOptionSaleUseCase kitOptionSaleUseCase;
	private final TransactionTemplate transactionTemplate;

	// 바깥 트랜잭션 없이 부른다
	public Participation change(long participationId, long memberId, List<Long> kitOptionIds, LocalDateTime now) {
		// 잠그기 전 확인. 여기서 읽은 신청은 저장하지 않는다(잠근 뒤 다시 읽는다)
		Participation found = participationRepository.findById(participationId)
			.orElseThrow(() -> new ParticipationNotFoundException("존재하지 않는 참가 신청입니다."));
		checkOwner(found, memberId);
		checkConfirmed(found);
		RaceCourseInfo course = raceCoursePort.findCourse(found.getRaceCourseId())
			.orElseThrow(() -> new RaceCourseNotFoundException("존재하지 않는 종목입니다."));
		ParticipationApplyUseCase.checkNotCanceled(course);
		if (!course.isBeforeClose(now)) {
			throw new ApplicationPeriodException("접수가 마감되어 키트 옵션을 바꿀 수 없습니다.");
		}
		List<KitOption> raceOptions = kitOptionPort.findKitOptions(found.getRaceId()).orElseThrow(() -> {
			// 취소되지 않은 대회인데 404다. 옵션 없음으로 넘기면 고른 옵션이 모두 지워지므로 막는다
			log.error("상품 서비스가 취소되지 않은 대회의 키트 옵션 목록에 404를 줬습니다. raceId={}", found.getRaceId());
			return new ExternalServiceUnavailableException(OrderPayUseCase.KIT_OPTIONS_UNAVAILABLE_MESSAGE);
		});
		List<KitOption> chosen = kitOptionSaleUseCase.choose(found.getRaceId(), raceOptions, kitOptionIds);
		return transactionTemplate.execute(status -> changeLocked(participationId, memberId, chosen));
	}

	private Participation changeLocked(long participationId, long memberId, List<KitOption> chosen) {
		long orderId = orderItemRepository.findOrderIdByParticipationId(participationId)
			.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다."));
		Order order = orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다."));
		Participation participation = participationRepository.findByIdForUpdate(participationId)
			.orElseThrow(() -> new ParticipationNotFoundException("존재하지 않는 참가 신청입니다."));
		checkOwner(participation, memberId);
		// 잠그기 전 확인 뒤에 취소됐을 수 있다
		checkConfirmed(participation);
		// 환불 결과를 기다리는 중이라 곧 취소될 수 있다. 참가자 정보 수정과 같이 막는다
		if (order.isCanceling(participationId)) {
			throw new ParticipationNotEditableException("취소 처리 중인 참가권이라 키트 옵션을 바꿀 수 없습니다.");
		}
		kitOptionSaleUseCase.change(participationId, chosen);
		return participation;
	}

	private static void checkOwner(Participation participation, long memberId) {
		if (!participation.isOwnedBy(memberId)) {
			throw new ParticipationAccessDeniedException("본인의 참가 신청만 보거나 고칠 수 있습니다.");
		}
	}

	// 키트 옵션은 결제 때 고르므로 결제한 참가권만 바꾼다. 결제 전이면 결제할 때 고른다
	private static void checkConfirmed(Participation participation) {
		if (participation.getStatus() != ParticipationStatus.CONFIRMED) {
			throw new ParticipationNotEditableException("결제한 참가권만 키트 옵션을 바꿀 수 있습니다.");
		}
	}
}
