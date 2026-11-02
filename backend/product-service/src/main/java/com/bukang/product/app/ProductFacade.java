package com.bukang.product.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.seller.event.SellerChangedEvent;
import com.bukang.product.domain.HoldReleaseReason;
import com.bukang.product.domain.ProductMember;
import com.bukang.product.domain.ProductSeller;
import com.bukang.product.dto.HoldRequestDto;
import com.bukang.product.dto.HoldResultDto;
import com.bukang.product.dto.HoldSellResult;
import com.bukang.product.dto.RaceCourseInfoDto;
import com.bukang.product.dto.RaceDetailDto;
import com.bukang.product.dto.RacePageDto;
import com.bukang.product.dto.RaceSaveDto;
import com.bukang.product.dto.RaceSaveRequestDto;
import com.bukang.product.dto.RaceScheduleDto;
import com.bukang.product.dto.RaceSearchCondition;
import com.bukang.product.dto.RaceSuggestionDto;
import com.bukang.product.dto.SellerRaceDto;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductFacade {
	private final ProductSyncMemberUseCase productSyncMemberUseCase;
	private final ProductSyncSellerUseCase productSyncSellerUseCase;
	private final RaceRegisterUseCase raceRegisterUseCase;
	private final RaceUpdateUseCase raceUpdateUseCase;
	private final RaceSearchUseCase raceSearchUseCase;
	private final RaceQueryUseCase raceQueryUseCase;
	private final RaceScheduleQueryUseCase raceScheduleQueryUseCase;
	private final RaceSaleStatusUseCase raceSaleStatusUseCase;
	private final RaceHoldUseCase raceHoldUseCase;
	private final RaceCancelUseCase raceCancelUseCase;
	private final TransactionTemplate transactionTemplate;
	private final ProductOutboxKafkaAppendUseCase productOutboxKafkaAppendUseCase;
	private final ProductOutboxKafkaRelayUseCase productOutboxKafkaRelayUseCase;
	private final RaceDocumentSyncAppendUseCase raceDocumentSyncAppendUseCase;
	private final RaceDocumentSyncProcessUseCase raceDocumentSyncProcessUseCase;

	@Transactional
	public ProductMember syncMember(long id, LocalDateTime createDate, LocalDateTime modifyDate, String nickname) {
		return productSyncMemberUseCase.syncMember(id, createDate, modifyDate, nickname);
	}

	@Transactional
	public Optional<ProductSeller> syncSeller(SellerChangedEvent event) {
		return productSyncSellerUseCase.syncSeller(event);
	}

	// 대회 저장(등록·수정)은 파일 연결·대회 변경·종목 변경 메시지(Kafka 아웃박스)와 검색 문서 동기화 이벤트를 같은 트랜잭션에서 남긴다
	// 저장이 롤백되면 두 행도 남지 않으므로 롤백된 대회에 파일을 연결하거나 문서를 바꾸지 않고, 커밋되면 반드시 남는다
	// (보내는 일은 ProductOutboxKafkaRelayScheduler, 문서를 맞추는 일은 RaceDocumentSyncScheduler가 한다)
	// NOT_SUPPORTED: 이 메서드는 트랜잭션 없이 실행한다 (클래스에 붙은 readOnly 트랜잭션을 쓰지 않는다)
	// transactionTemplate.execute(): 넘긴 람다만 새 트랜잭션으로 실행하고, execute()가 값을 돌려주기 전에 커밋한다
	// 그래서 다음 줄로 넘어온 시점에는 저장이 이미 커밋되어 있고, 저장이 실패하면 예외가 나서 다음 줄로 오지 않는다
	// 같은 판매자가 같은 Idempotency-Key로 다시 보내면(두 번 클릭, 응답을 못 받은 재시도) 새로 만들지 않고 처음 대회를 돌려준다
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public RaceRegisterResult registerRace(long memberId, String idempotencyKey, RaceSaveRequestDto request) {
		Optional<RaceSaveDto> registered = findRegisteredRace(memberId, idempotencyKey);
		if (registered.isPresent()) {
			return RaceRegisterResult.existing(registered.get());
		}
		RaceSaveResult result;
		try {
			result = transactionTemplate.execute(status -> {
				RaceSaveResult saved = raceRegisterUseCase.register(memberId, idempotencyKey, request,
					LocalDateTime.now());
				appendRaceSaved(saved);
				return saved;
			});
		} catch (DataIntegrityViolationException exception) {
			// 같은 키로 거의 동시에 보내 늦은 쪽이 유니크 제약(uk_race_idempotency)에 걸렸다
			// 늦은 쪽 트랜잭션은 롤백되었으므로, 먼저 등록된 대회를 새 트랜잭션에서 읽어 돌려준다 (자리 확보 holdSeat와 같은 방식)
			// 읽어도 없으면 키가 아닌 다른 제약에 걸린 것이라 그대로 던진다
			RaceSaveDto first = findRegisteredRace(memberId, idempotencyKey).orElseThrow(() -> exception);
			return RaceRegisterResult.existing(first);
		}
		return RaceRegisterResult.created(result.dto());
	}

	private Optional<RaceSaveDto> findRegisteredRace(long memberId, String idempotencyKey) {
		return transactionTemplate.execute(status -> raceRegisterUseCase.findRegistered(memberId, idempotencyKey));
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public RaceSaveDto updateRace(long memberId, long raceId, RaceSaveRequestDto request) {
		RaceSaveResult result = transactionTemplate.execute(status -> {
			RaceSaveResult saved = raceUpdateUseCase.update(memberId, raceId, request);
			appendRaceSaved(saved);
			return saved;
		});
		return result.dto();
	}

	// 판매자가 대회를 취소한다. 주문·대기열 서비스에 보낼 취소 메시지와 검색 문서 동기화 이벤트(공개 목록에서 빠짐)를
	// 같은 트랜잭션에서 남긴다
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public RaceSaveDto cancelRace(long memberId, long raceId) {
		RaceCancelResult result = transactionTemplate.execute(status -> {
			RaceCancelResult canceled = raceCancelUseCase.cancel(memberId, raceId, LocalDateTime.now());
			productOutboxKafkaAppendUseCase.appendRaceEvent(KafkaTopics.ORDER_RACE_CANCEL, raceId,
				canceled.cancelEvent());
			appendRaceChanged(canceled.saved());
			raceDocumentSyncAppendUseCase.append(raceId);
			return canceled;
		});
		return result.saved().dto();
	}

	// 주문 서비스의 [결제하기] 자리 확보 (내부 API)
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public HoldResultDto holdSeat(HoldRequestDto request) {
		try {
			return transactionTemplate.execute(status -> raceHoldUseCase.hold(request, LocalDateTime.now()));
		} catch (DataIntegrityViolationException exception) {
			// 같은 신청으로 동시에 확보를 요청해 늦은 쪽이 유니크 제약(application_id)에 걸렸다
			// 늦은 쪽 트랜잭션은 카운터를 올린 것까지 롤백되었으므로, 먼저 잡힌 자리의 결과를 돌려준다
			return transactionTemplate.execute(status ->
				raceHoldUseCase.findExisting(request.getParticipationId()));
		}
	}

	@Transactional
	public HoldSellResult sellSeat(long participationId) {
		return raceHoldUseCase.sell(participationId, LocalDateTime.now());
	}

	@Transactional
	public boolean restoreSeat(long participationId) {
		return raceHoldUseCase.restore(participationId, LocalDateTime.now());
	}

	// 대기열 활성 시간이 끝나 주문 서비스가 보낸 해제 요청 (Kafka product.hold.cancel)
	@Transactional
	public boolean cancelHold(long participationId) {
		return raceHoldUseCase.release(participationId, HoldReleaseReason.EXPIRED, LocalDateTime.now());
	}

	public RacePageDto searchRaces(RaceSearchCondition condition, int page, int size) {
		return raceSearchUseCase.search(condition, page, size);
	}

	public RaceSuggestionDto suggestRaces(String keyword) {
		return raceSearchUseCase.suggest(keyword);
	}

	public RaceDetailDto findRace(long raceId, Long memberId) {
		return raceQueryUseCase.findDetail(raceId, memberId);
	}

	public List<SellerRaceDto> findMyRaces(long memberId) {
		return raceQueryUseCase.findMine(memberId);
	}

	public RaceCourseInfoDto findCourseInfo(long raceCourseId) {
		return raceQueryUseCase.findCourseInfo(raceCourseId);
	}

	// 정산 서비스의 1차 정산 대상 대회 (내부 API)
	public List<RaceScheduleDto> findRacesByRefundDeadline(LocalDate from, LocalDate to) {
		return raceScheduleQueryUseCase.findByRefundDeadline(from, to);
	}

	// 정산 서비스의 최종 정산 대상 대회 (내부 API)
	public List<RaceScheduleDto> findRacesByRaceDate(LocalDate from, LocalDate to) {
		return raceScheduleQueryUseCase.findByRaceDate(from, to);
	}

	// 시각이 지난 대회의 판매 상태를 바꾸고, 바뀐 대회마다 대기열에 보낼 대회 변경 메시지와 검색 문서 동기화 이벤트를 남긴다
	// 상태가 바뀐 대회 수를 돌려준다
	@Transactional
	public int refreshSaleStatuses() {
		RaceSaleStatusResult result = raceSaleStatusUseCase.refresh(LocalDateTime.now());
		result.raceChanges().forEach(event -> {
			productOutboxKafkaAppendUseCase.appendRaceEvent(KafkaTopics.RACE_CHANGED, event.raceId(), event);
			raceDocumentSyncAppendUseCase.append(event.raceId());
		});
		return result.raceChanges().size();
	}

	// 아웃박스의 미발행 메시지를 Kafka로 보낸다 (ProductOutboxKafkaRelayScheduler가 1초마다 부른다). 보낸 건수를 돌려준다
	@Transactional
	public int relayKafkaOutbox() {
		return productOutboxKafkaRelayUseCase.relay(LocalDateTime.now());
	}

	// 미처리 동기화 이벤트를 처리해 대회 검색 문서를 DB에 맞춘다 (RaceDocumentSyncScheduler가 1초마다 부른다)
	// 문서를 맞춘 대회 수를 돌려준다
	@Transactional
	public int syncRaceDocuments() {
		return raceDocumentSyncProcessUseCase.process(LocalDateTime.now());
	}

	// 대회 저장 메시지와 검색 문서 동기화 이벤트를 남긴다 (대회 저장 트랜잭션 안에서 부른다)
	private void appendRaceSaved(RaceSaveResult result) {
		appendFileLink(result);
		appendRaceChanged(result);
		appendRaceCourseChanged(result);
		raceDocumentSyncAppendUseCase.append(result.raceId());
	}

	// file-service에 보낼 이미지 연결 메시지 (race.filelink.requested)
	// 연결되지 않은 파일은 하루 뒤 file-service 정리 배치가 지우므로, 메시지가 SEND_FAILED로 남으면 그 전에 다시 보내야 한다
	private void appendFileLink(RaceSaveResult result) {
		productOutboxKafkaAppendUseCase.appendRaceEvent(KafkaTopics.RACE_FILE_LINK_REQUESTED, result.raceId(),
			result.fileLink());
	}

	// 대기열 서비스에 보낼 대회 변경 메시지 (queuing.race.changed). 대회 칸이 바뀌지 않은 수정이면 남기지 않는다
	private void appendRaceChanged(RaceSaveResult result) {
		if (result.raceChanged() != null) {
			productOutboxKafkaAppendUseCase.appendRaceEvent(KafkaTopics.RACE_CHANGED, result.raceId(),
				result.raceChanged());
		}
	}

	// 대기열 서비스에 보낼 종목 변경 메시지. 종목마다 1건씩 남긴다 (queuing.racecourse.changed)
	// 같은 대회의 메시지가 순서대로 처리되도록 종목 메시지도 대회 ID를 key로 보낸다 (같은 key는 같은 파티션)
	private void appendRaceCourseChanged(RaceSaveResult result) {
		result.courseChanges().forEach(event ->
			productOutboxKafkaAppendUseCase.appendRaceEvent(KafkaTopics.RACE_COURSE_CHANGED, result.raceId(), event));
	}
}
