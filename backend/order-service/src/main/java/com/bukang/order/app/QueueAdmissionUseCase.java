package com.bukang.order.app;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.QueueAdmission;
import com.bukang.order.dto.QueueAdmissionDto;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.out.queue.QueueAdmissionPort;

/**
 * 신청서·결제 페이지 타이머에 쓸 대기열 입장 상태를 돌려준다 (PRO-191)
 * 대기열에는 여기서만 묻는다. 입장 중이면 끝 시각을 저장하고, [결제하기](OrderCreateUseCase)는 대기열 대신 저장값을 읽는다
 * (10/17 회의 2번, PRO-249). 그래서 타이머와 결제 기한이 같은 끝 시각이고, 신청서를 쓰는 동안 대기열이 멈춰도 결제할 수 있다
 * 실제 대기열(HttpQueueAdmissionAdapter, PRO-226)이든 가짜든 프론트가 부르는 주소는 같다
 */
@Service
public class QueueAdmissionUseCase {
	private final QueueAdmissionPort queueAdmissionPort;
	private final QueueAdmissionRepository queueAdmissionRepository;
	// 저장은 늘 새 트랜잭션에서 한다. 바깥 트랜잭션 안에서 불려도 같은 줄 충돌이 바깥을 롤백 전용으로 만들지 않고 다시 시도할 수 있다
	private final TransactionTemplate requiresNew;

	public QueueAdmissionUseCase(QueueAdmissionPort queueAdmissionPort,
		QueueAdmissionRepository queueAdmissionRepository, PlatformTransactionManager transactionManager) {
		this.queueAdmissionPort = queueAdmissionPort;
		this.queueAdmissionRepository = queueAdmissionRepository;
		this.requiresNew = new TransactionTemplate(transactionManager);
		this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public QueueAdmissionDto find(long memberId, long raceId, long raceCourseId, LocalDateTime now) {
		LocalDateTime endsAt = queueAdmissionPort.findAdmissionEndsAtOnPageOpen(memberId, raceId, raceCourseId, now)
			.orElse(null);
		QueueAdmissionDto admission = QueueAdmissionDto.of(endsAt, now);
		if (admission.isAdmitted()) {
			remember(memberId, raceCourseId, endsAt);
		}
		return admission;
	}

	// 입장 전이면 저장하지 않는다. 예전 끝 시각이 남아 있어도 이미 지났으므로 [결제하기]는 403이다
	private void remember(long memberId, long raceCourseId, LocalDateTime endsAt) {
		try {
			requiresNew.executeWithoutResult(status -> save(memberId, raceCourseId, endsAt));
		} catch (DataIntegrityViolationException exception) {
			// 같은 회원이 처음 진입하며 동시에 두 번 물으면 한쪽이 먼저 넣는다. 그 줄을 이 끝 시각으로 고친다
			requiresNew.executeWithoutResult(status -> save(memberId, raceCourseId, endsAt));
		}
	}

	private void save(long memberId, long raceCourseId, LocalDateTime endsAt) {
		queueAdmissionRepository.findByMemberIdAndRaceCourseId(memberId, raceCourseId)
			.ifPresentOrElse(saved -> saved.admitUntil(endsAt),
				() -> queueAdmissionRepository.saveAndFlush(QueueAdmission.of(memberId, raceCourseId, endsAt)));
	}
}
