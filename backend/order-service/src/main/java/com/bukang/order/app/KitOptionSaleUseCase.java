package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.KitOptionSale;
import com.bukang.order.domain.ParticipationKitOption;
import com.bukang.order.domain.exception.KitOptionInvalidException;
import com.bukang.order.domain.exception.KitOptionSoldOutException;
import com.bukang.order.domain.exception.SellerAccessDeniedException;
import com.bukang.order.dto.SellerKitOptionSaleDto;
import com.bukang.order.out.KitOptionSaleRepository;
import com.bukang.order.out.OrderItemRepository;
import com.bukang.order.out.ParticipationKitOptionRepository;
import com.bukang.order.out.kit.KitOptionPort;
import com.bukang.order.out.kit.KitOptionPort.KitOption;
import com.bukang.order.out.race.RaceCourseInfo;

/**
 * 키트 옵션 고르기와 판매 수 세기 (11/07·11/09 결정). 상품은 준비 수량(stock)만 주고 판매 수는 주문이 센다
 * - 결제: 잠그기 전에 고른 옵션을 확인하고(choose), 예치금 결제 전에 판매 수를 +1 한다(reserve). 결제가 실패하면 함께 되돌아간다
 * - 결제한 참가권 취소: 고른 옵션의 판매 수를 -1 한다(release). 참가 확정 → 취소와 같은 트랜잭션에서 부르므로 한 번만 줄어든다
 * 잠금 순서: 주문 → 참가 신청 → 판매 수 줄(옵션 ID 오름차순). 결제·취소가 같은 순서라 서로 막혀 멈추지 않는다
 */
@Service
public class KitOptionSaleUseCase {
	static final String SOLD_OUT_MESSAGE = "품절된 옵션이에요. 다시 골라 주세요. (옵션: %s %s)";

	private final KitOptionPort kitOptionPort;
	private final KitOptionSaleRepository kitOptionSaleRepository;
	private final ParticipationKitOptionRepository participationKitOptionRepository;
	private final OrderItemRepository orderItemRepository;
	// 판매 수 줄을 처음 만들 때만 쓴다. 만든 줄은 결제가 실패해도 남는다(판매 수 0)
	private final TransactionTemplate newTransaction;

	public KitOptionSaleUseCase(KitOptionPort kitOptionPort, KitOptionSaleRepository kitOptionSaleRepository,
		ParticipationKitOptionRepository participationKitOptionRepository, OrderItemRepository orderItemRepository,
		PlatformTransactionManager transactionManager) {
		this.kitOptionPort = kitOptionPort;
		this.kitOptionSaleRepository = kitOptionSaleRepository;
		this.participationKitOptionRepository = participationKitOptionRepository;
		this.orderItemRepository = orderItemRepository;
		this.newTransaction = new TransactionTemplate(transactionManager);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * 고른 옵션을 확인하고, 처음 팔리는 옵션이면 판매 수 줄을 만든다. 주문을 잠그기 전에(트랜잭션 밖에서) 부른다
	 * 상품 HTTP와 줄 만들기가 잠금·DB 연결을 쥔 채 일어나지 않게 한다 (pending 48). 고른 옵션을 돌려준다(옵션 ID 오름차순)
	 * 규칙 (추천값(미확정)): 키트 없는 종목은 옵션을 받지 않는다. 키트 있는 종목은 대회 옵션 이름마다 정확히 하나씩 고른다.
	 * 대회에 옵션이 없으면 아무것도 고르지 않는다
	 */
	public List<KitOption> choose(RaceCourseInfo course, Collection<Long> kitOptionIds) {
		Set<Long> ids = kitOptionIds == null ? Set.of() : new TreeSet<>(kitOptionIds);
		if (kitOptionIds != null && ids.size() != kitOptionIds.size()) {
			throw new KitOptionInvalidException("같은 키트 옵션을 두 번 골랐습니다.");
		}
		if (!course.kitIncluded()) {
			if (!ids.isEmpty()) {
				throw new KitOptionInvalidException("키트가 없는 종목은 키트 옵션을 고를 수 없습니다.");
			}
			return List.of();
		}
		List<KitOption> chosen = choose(kitOptionPort.findKitOptions(course.raceId()), ids);
		chosen.forEach(option -> createSaleRowIfAbsent(option.kitOptionId(), course.raceId()));
		return chosen;
	}

	/**
	 * 고른 옵션의 판매 수를 +1 한다. 결제 트랜잭션 안에서, 예치금 결제 바로 전에 부른다. 품절이면 409
	 * 결제가 실패하면 같은 트랜잭션이라 함께 되돌아간다. 늘린 줄은 커밋까지 잠겨 같은 옵션의 다른 결제가 기다린다
	 */
	public void reserve(List<KitOption> chosen) {
		// chosen이 옵션 ID 오름차순이라 줄을 늘 같은 순서로 잠근다
		for (KitOption option : chosen) {
			KitOptionSale sale = kitOptionSaleRepository.findByIdForUpdate(option.kitOptionId())
				.orElseThrow(() -> new IllegalStateException(
					"키트 옵션 판매 수 줄이 없습니다. kitOptionId=" + option.kitOptionId()));
			if (!sale.increase(option.stock())) {
				throw new KitOptionSoldOutException(SOLD_OUT_MESSAGE.formatted(option.name(), option.value()));
			}
		}
	}

	private static List<KitOption> choose(List<KitOption> raceOptions, Set<Long> ids) {
		Map<Long, KitOption> byId = raceOptions.stream()
			.collect(Collectors.toMap(KitOption::kitOptionId, Function.identity()));
		List<KitOption> chosen = ids.stream()
			.map(id -> {
				KitOption option = byId.get(id);
				if (option == null) {
					throw new KitOptionInvalidException("이 대회의 키트 옵션이 아닙니다. (옵션 ID: %d)".formatted(id));
				}
				return option;
			})
			.toList();
		Set<String> chosenNames = new HashSet<>();
		for (KitOption option : chosen) {
			if (!chosenNames.add(option.name())) {
				throw new KitOptionInvalidException("옵션 이름마다 하나만 고를 수 있습니다. (옵션: %s)".formatted(option.name()));
			}
		}
		Set<String> missing = raceOptions.stream()
			.map(KitOption::name)
			.filter(name -> !chosenNames.contains(name))
			.collect(Collectors.toCollection(TreeSet::new));
		if (!missing.isEmpty()) {
			throw new KitOptionInvalidException("키트 옵션을 모두 골라 주세요. (고르지 않은 옵션: %s)"
				.formatted(String.join(", ", missing)));
		}
		return chosen;
	}

	/**
	 * 옵션의 첫 판매면 판매 수 줄(0)을 따로 커밋해 만든다. 줄이 있어야 결제가 잠그고 셀 수 있다 (줄은 지우지 않는다)
	 * 두 결제가 동시에 만들면 한쪽은 기본 키 중복으로 실패하는데, 다른 쪽이 만들었으니 그대로 넘어간다
	 * 결제 트랜잭션 밖에서 부르므로 연결을 하나 더 쓰지 않는다 (테스트처럼 바깥 트랜잭션이 있어도 따로 커밋한다)
	 */
	private void createSaleRowIfAbsent(long kitOptionId, long raceId) {
		if (kitOptionSaleRepository.existsById(kitOptionId)) {
			return;
		}
		try {
			newTransaction.executeWithoutResult(
				status -> kitOptionSaleRepository.insertEmpty(kitOptionId, raceId, LocalDateTime.now()));
		} catch (DataIntegrityViolationException exception) {
			// 다른 결제가 먼저 만들었다
		}
	}

	// 결제가 끝난 참가 신청에 고른 옵션의 사본(이름·값)을 남긴다
	public void saveChoices(long participationId, List<KitOption> chosen) {
		participationKitOptionRepository.saveAll(chosen.stream()
			.map(option -> ParticipationKitOption.of(participationId, option.kitOptionId(), option.name(),
				option.value()))
			.toList());
	}

	// 결제한 참가권이 취소됐다. 참가 신청을 확정 → 취소로 바꾸는 트랜잭션 안에서 부른다
	public void release(long participationId) {
		Set<Long> ids = participationKitOptionRepository.findByParticipationIdOrderByIdAsc(participationId).stream()
			.map(ParticipationKitOption::getKitOptionId)
			.collect(Collectors.toCollection(TreeSet::new));
		if (!ids.isEmpty()) {
			kitOptionSaleRepository.findAllByIdForUpdate(ids).forEach(KitOptionSale::decrease);
		}
	}

	/**
	 * 판매자 키트 옵션별 판매 현황. 옵션과 준비 수량은 상품 목록에서, 판매 수는 이 서비스의 판매 수 표에서 읽는다
	 * shortcut: 상품 목록에 판매자 ID가 없어, 이 대회 주문 상품에 다른 판매자가 있을 때만 403으로 막는다.
	 * 아직 팔린 것이 없는 남의 대회는 판매 수 0과 공개된 준비 수량만 보인다. 상품 대회 조회에 판매자 확인이 생기면 바꾼다
	 */
	public List<SellerKitOptionSaleDto> findSales(long sellerId, long raceId) {
		if (orderItemRepository.existsByRaceIdAndSellerIdNot(raceId, sellerId)) {
			throw new SellerAccessDeniedException("본인 대회의 키트 옵션만 볼 수 있습니다.");
		}
		Map<Long, Integer> soldById = kitOptionSaleRepository.findByRaceId(raceId).stream()
			.collect(Collectors.toMap(KitOptionSale::getId, KitOptionSale::getSold));
		return kitOptionPort.findKitOptions(raceId).stream()
			.map(option -> SellerKitOptionSaleDto.of(option.kitOptionId(), option.name(), option.value(),
				option.stock(), soldById.getOrDefault(option.kitOptionId(), 0)))
			.toList();
	}
}
