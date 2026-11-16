package com.bukang.order.app;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.bukang.common.global.exception.BusinessException;
import com.bukang.order.out.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 오래 남은 결제 중 주문을 한 회차에 다 맞춘다 (결제 재확인 배치, BUK-232)
 * 묶음(BATCH_SIZE)을 읽어 하나씩 OrderPayUseCase.recheck를 부르고, 남은 주문이 없을 때까지 다시 읽는다
 * 시간 상한(budget)을 넘거나, 결과를 모르는 주문이 잇달아 나오면(예치금·상품 장애) 그 회차를 멈추고 다음 회차에 넘긴다
 * 바깥 트랜잭션 없이 부른다 (OrderPayRecheckScheduler)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPayRecheckUseCase {
	// 한 번에 읽을 주문 수 (추천값(미확정))
	static final int BATCH_SIZE = 30;
	// 결과를 모르는 주문이 이만큼 잇달아 나오면 회차를 멈춘다. 장애 중에 호출 시간 제한(2초)마다 계속 두드리지 않는다 (추천값(미확정))
	static final int FAILURE_LIMIT = 5;

	private final OrderRepository orderRepository;
	private final OrderPayUseCase orderPayUseCase;

	public enum Stop { DONE, TIME_LIMIT, FAILURES }

	public record Run(int checked, int finished, Stop stop) {
	}

	/**
	 * @param staleBefore 이 시각 이전에 결제 중이 된 주문만 본다 (요청이 아직 예치금·상품을 부르는 중일 수 있다)
	 * @param budget 한 회차의 시간 상한. 한 건을 끝낼 때마다 보고, 넘으면 멈춘다(적어도 한 건은 본다)
	 */
	public Run recheckStale(LocalDateTime now, LocalDateTime staleBefore, Duration budget) {
		long deadline = System.nanoTime() + budget.toNanos();
		// 결과를 모른 주문은 시도 횟수가 늘어 뒤로 가지만 다시 읽힐 수 있다. 한 회차에 한 번만 본다
		Set<Long> seen = new HashSet<>();
		int checked = 0;
		int finished = 0;
		int failures = 0;
		while (true) {
			List<Long> orderIds = orderRepository.findStalePayingIds(staleBefore, PageRequest.of(0, BATCH_SIZE));
			List<Long> fresh = orderIds.stream().filter(seen::add).toList();
			if (fresh.isEmpty()) {
				return new Run(checked, finished, Stop.DONE);
			}
			for (long orderId : fresh) {
				checked++;
				if (recheckOne(orderId, now)) {
					finished++;
					failures = 0;
				} else if (++failures >= FAILURE_LIMIT) {
					return new Run(checked, finished, Stop.FAILURES);
				}
				if (System.nanoTime() - deadline >= 0) {
					return new Run(checked, finished, Stop.TIME_LIMIT);
				}
			}
			if (orderIds.size() < BATCH_SIZE) {
				return new Run(checked, finished, Stop.DONE);
			}
		}
	}

	// 결제 완료이거나 확실한 결과(되돌림·이미 끝남)로 끝냈으면 true, 결과를 모르면 false (주문이 그새 다른 곳에서 끝났어도 false)
	// 한 건의 실패가 회차를 멈추지 않는다
	private boolean recheckOne(long orderId, LocalDateTime now) {
		try {
			return orderPayUseCase.recheck(orderId, now);
		} catch (BusinessException exception) {
			// 잔액 부족·자리 기한 지남처럼 확실한 실패(되돌렸다)이거나, 확정 때 주문이 이미 끝나 있었다
			log.info("결제 중 주문이 결제 완료가 아닌 결과로 끝났습니다. orderId={}, cause={}", orderId, exception.getMessage());
			return true;
		} catch (RuntimeException exception) {
			log.warn("결제 중 주문을 다시 확인하지 못했습니다. orderId={}, cause={}", orderId, exception.toString());
			return false;
		}
	}
}
