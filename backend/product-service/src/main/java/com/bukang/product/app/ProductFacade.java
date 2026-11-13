package com.bukang.product.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventDto;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.common.shared.kafka.dlt.DltKafkaResendException;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.product.domain.HoldReleaseReason;
import com.bukang.product.domain.ProductDltKafkaEvent;
import com.bukang.product.dto.CrewHoldRequestDto;
import com.bukang.product.dto.CrewHoldResultDto;
import com.bukang.product.dto.CrewLowPaymentDto;
import com.bukang.product.dto.HoldRequestDto;
import com.bukang.product.dto.HoldResultDto;
import com.bukang.product.dto.HoldSellResult;
import com.bukang.product.dto.KitOptionListResponseDto;
import com.bukang.product.dto.KitOptionSaveRequestDto;
import com.bukang.product.dto.RaceCourseInfoDto;
import com.bukang.product.dto.RaceDetailDto;
import com.bukang.product.dto.RacePageDto;
import com.bukang.product.dto.RaceSaveDto;
import com.bukang.product.dto.RaceSaveRequestDto;
import com.bukang.product.dto.RaceScheduleDto;
import com.bukang.product.dto.RaceSearchCondition;
import com.bukang.product.dto.RaceSellerMemberDto;
import com.bukang.product.dto.RaceSuggestionDto;
import com.bukang.product.dto.SellerRaceDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductFacade {
	// 인박스에 남길 컨슈머 그룹. ProductKafkaConsumer의 groupId와 같은 값이다
	private static final String KAFKA_GROUP_ID = "product";

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
	private final RaceSaleStopUseCase raceSaleStopUseCase;
	private final RaceKitOptionUseCase raceKitOptionUseCase;
	private final CrewSeatBatchUseCase crewSeatBatchUseCase;
	private final CrewLowPaymentQueryUseCase crewLowPaymentQueryUseCase;
	private final TransactionTemplate transactionTemplate;
	private final ProductOutboxKafkaAppendUseCase productOutboxKafkaAppendUseCase;
	private final ProductOutboxKafkaRelayUseCase productOutboxKafkaRelayUseCase;
	private final RaceDocumentSyncAppendUseCase raceDocumentSyncAppendUseCase;
	private final RaceDocumentSyncProcessUseCase raceDocumentSyncProcessUseCase;
	private final ProductInboxUseCase productInboxUseCase;
	private final ProductDltUseCase productDltUseCase;

	// Kafka로 받은 회원 가입·수정 메시지. 인박스에 남긴 뒤 회원 복제본에 덮어쓰고, 이미 처리한 메시지면 건너뛴다
	// 인박스 행과 복제본 저장이 한 트랜잭션이라, 저장이 실패해 롤백되면 다시 받은 메시지를 처리한다
	// DLT에서 다시 보낸 메시지였으면 같은 트랜잭션에서 실패 행을 처리 완료로 바꾼다 (Kafka 메시지 4개 메서드 모두 같다)
	@Transactional
	public void syncMember(String messageId, String topic, long id, LocalDateTime createDate,
		LocalDateTime modifyDate, String nickname) {
		LocalDateTime now = LocalDateTime.now();
		if (productInboxUseCase.tryRecord(messageId, KAFKA_GROUP_ID, topic, now)) {
			productSyncMemberUseCase.syncMember(id, createDate, modifyDate, nickname);
		}
		productDltUseCase.completeByResend(messageId, now);
	}

	// Kafka로 받은 판매자 변경 메시지. 인박스에 남긴 뒤 판매자 복제본에 덮어쓰고, 이미 처리한 메시지면 건너뛴다
	// 회원 복제본이 없는 판매자 메시지는 예외 없이 건너뛰므로 인박스에는 남는다 (다시 받아도 건너뛴다)
	@Transactional
	public void syncSeller(String messageId, String topic, long id, long memberId, SellerType sellerType,
		String businessName, String businessNumber, String representativeName, SellerStatus status,
		LocalDateTime requestedAt, LocalDateTime reviewedAt) {
		LocalDateTime now = LocalDateTime.now();
		if (productInboxUseCase.tryRecord(messageId, KAFKA_GROUP_ID, topic, now)) {
			productSyncSellerUseCase.syncSeller(id, memberId, sellerType, businessName, businessNumber,
				representativeName, status, requestedAt, reviewedAt);
		}
		productDltUseCase.completeByResend(messageId, now);
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

	// 판매자가 판매를 멈춘다. 대기열에 보낼 대회 변경 메시지(판매 중지 → 입장 막힘)와 검색 문서 동기화 이벤트를
	// 같은 트랜잭션에서 남긴다. 판매 중지 대회도 공개 목록에는 남는다 (검색 문서의 판매 상태만 바뀐다)
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public RaceSaveDto stopRace(long memberId, long raceId) {
		return transactionTemplate.execute(status -> {
			RaceSaveResult stopped = raceSaleStopUseCase.stop(memberId, raceId, LocalDateTime.now());
			appendRaceChanged(stopped);
			raceDocumentSyncAppendUseCase.append(raceId);
			return stopped.dto();
		});
	}

	// 판매자가 판매를 다시 연다. 메시지와 검색 문서는 멈추기와 같이 남긴다 (대기열 입장이 다시 열린다)
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public RaceSaveDto resumeRace(long memberId, long raceId) {
		return transactionTemplate.execute(status -> {
			RaceSaveResult resumed = raceSaleStopUseCase.resume(memberId, raceId, LocalDateTime.now());
			appendRaceChanged(resumed);
			raceDocumentSyncAppendUseCase.append(raceId);
			return resumed.dto();
		});
	}

	// 판매자가 대회에 키트 옵션을 처음 등록한다. 주문은 결제 때 목록을 HTTP로 읽으므로 메시지·검색 문서는 남기지 않는다
	@Transactional
	public KitOptionListResponseDto registerKitOptions(long memberId, long raceId,
		List<KitOptionSaveRequestDto> requests) {
		return raceKitOptionUseCase.register(memberId, raceId, requests, LocalDateTime.now());
	}

	// 판매자가 대회의 키트 옵션을 통째로 바꾼다 (메시지는 등록과 같이 남기지 않는다)
	@Transactional
	public KitOptionListResponseDto updateKitOptions(long memberId, long raceId,
		List<KitOptionSaveRequestDto> requests) {
		return raceKitOptionUseCase.update(memberId, raceId, requests, LocalDateTime.now());
	}

	// 주문 서비스의 [결제하기] 자리 확보 (내부 API)
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public HoldResultDto holdSeat(HoldRequestDto request) {
		try {
			return transactionTemplate.execute(status -> raceHoldUseCase.hold(request, LocalDateTime.now()));
		} catch (DataIntegrityViolationException exception) {
			// 같은 신청으로 동시에 확보를 요청해 늦은 쪽이 유니크 제약(participation_id)에 걸렸다
			// 늦은 쪽 트랜잭션은 카운터를 올린 것까지 롤백되었으므로, 먼저 잡힌 자리의 결과를 돌려준다
			return transactionTemplate.execute(status ->
				raceHoldUseCase.findExisting(request.getParticipationId()));
		}
	}

	// 주문 서비스의 파티 [대회 참가] 크루 자리 확보 (내부 API). 파티원 수만큼 한꺼번에, 모자라면 한 석도 잡지 않는다
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public CrewHoldResultDto holdCrewSeats(CrewHoldRequestDto request) {
		// 요청 DTO를 UseCase 안쪽으로 넘기지 않고 값만 풀어 넘긴다
		List<RaceHoldUseCase.CrewSeat> seats = request.getSeats().stream()
			.map(seat -> new RaceHoldUseCase.CrewSeat(seat.getParticipationId(), seat.getRaceCourseId()))
			.toList();
		try {
			return transactionTemplate.execute(status -> raceHoldUseCase.holdCrew(request.getPartyId(),
				request.getCrewId(), request.getRaceId(), request.getExpiresAt(), seats, LocalDateTime.now()));
		} catch (DataIntegrityViolationException exception) {
			// 같은 파티로 동시에 확보를 요청해 늦은 쪽이 유니크 제약(participation_id)에 걸렸다
			// 늦은 쪽 트랜잭션은 카운터를 올린 것까지 롤백되었으므로, 먼저 잡힌 자리의 결과를 돌려준다
			return transactionTemplate.execute(status ->
				raceHoldUseCase.findExistingCrew(request.getPartyId(), seats));
		}
	}

	@Transactional
	public HoldSellResult sellSeat(long participationId) {
		return raceHoldUseCase.sell(participationId, LocalDateTime.now());
	}

	// Kafka로 받은 판매 자리 복구 메시지 (product.hold.restore). 인박스에 남긴 뒤 되돌리고, 이미 처리한 메시지면 건너뛴다
	@Transactional
	public void restoreSeat(String messageId, String topic, long participationId) {
		LocalDateTime now = LocalDateTime.now();
		if (productInboxUseCase.tryRecord(messageId, KAFKA_GROUP_ID, topic, now)) {
			raceHoldUseCase.restore(participationId, now);
		}
		productDltUseCase.completeByResend(messageId, now);
	}

	// 대기열 활성 시간이 끝나 주문 서비스가 보낸 해제 요청 (Kafka product.hold.cancel)
	// 인박스에 남긴 뒤 해제하고, 이미 처리한 메시지면 건너뛴다
	@Transactional
	public void cancelHold(String messageId, String topic, long participationId) {
		LocalDateTime now = LocalDateTime.now();
		if (productInboxUseCase.tryRecord(messageId, KAFKA_GROUP_ID, topic, now)) {
			raceHoldUseCase.release(participationId, HoldReleaseReason.EXPIRED, now);
		}
		productDltUseCase.completeByResend(messageId, now);
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

	public KitOptionListResponseDto findKitOptions(long raceId, Long memberId) {
		return raceQueryUseCase.findKitOptions(raceId, memberId);
	}

	public List<SellerRaceDto> findMyRaces(long memberId) {
		return raceQueryUseCase.findMine(memberId);
	}

	public RaceCourseInfoDto findCourseInfo(long raceCourseId) {
		return raceQueryUseCase.findCourseInfo(raceCourseId);
	}

	// 문의 서비스가 문의를 받을 판매자 회원을 확인한다 (내부 API, BUK-159)
	public RaceSellerMemberDto findRaceSellerMember(long raceId) {
		return raceQueryUseCase.findSellerMember(raceId);
	}

	// 정산 서비스의 1차 정산 대상 대회 (내부 API)
	public List<RaceScheduleDto> findRacesByRefundDeadline(LocalDate from, LocalDate to) {
		return raceScheduleQueryUseCase.findByRefundDeadline(from, to);
	}

	// 정산 서비스의 최종 정산 대상 대회 (내부 API)
	public List<RaceScheduleDto> findRacesByRaceDate(LocalDate from, LocalDate to) {
		return raceScheduleQueryUseCase.findByRaceDate(from, to);
	}

	// 회원 서비스가 크루 참가 제한을 기록할 때 쓰는 결제율 미달 크루 (내부 API, BUK-176)
	// 클래스의 읽기 전용 트랜잭션 안에서 센다
	public List<CrewLowPaymentDto> findLowPaymentCrews(YearMonth month) {
		return crewLowPaymentQueryUseCase.find(month);
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

	// 크루 자리 매시간 배치. CrewSeatBatchScheduler가 매시 정각의 시각을, 테스트는 고정 시각을 넣어 부른다
	// 클래스의 읽기 전용 트랜잭션 밖에서(NOT_SUPPORTED) 돌려야 아래 transactionTemplate이 단계·종목마다 따로 트랜잭션을 연다
	// ① 결제 기한이 지난 결제 대기 크루 자리 반환은 트랜잭션 하나로 한다
	// ② 크루 몫 이동은 종목마다 따로 트랜잭션으로 한다. 한 종목이 실패해도 나머지 종목은 옮기고, 실패한 종목은 다음 정각에 다시 고른다
	//   (반환·이동이 모두 조건부 UPDATE라 다시 돌아도 한 번만 반영된다)
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public CrewSeatBatchUseCase.Result runCrewSeatBatch(LocalDateTime now) {
		Integer released = transactionTemplate.execute(status -> crewSeatBatchUseCase.releaseExpiredCrew(now));
		int closedCourses = 0;
		for (long raceCourseId : crewSeatBatchUseCase.findClosableCourseIds(now)) {
			try {
				if (Boolean.TRUE.equals(transactionTemplate.execute(status ->
					crewSeatBatchUseCase.closeCrew(raceCourseId, now)))) {
					closedCourses++;
				}
			} catch (RuntimeException exception) {
				log.error("크루 몫을 개인 몫으로 옮기지 못했습니다. 다음 정각에 다시 시도합니다. raceCourseId={}", raceCourseId,
					exception);
			}
		}
		return new CrewSeatBatchUseCase.Result(released == null ? 0 : released, closedCourses);
	}

	// 아웃박스의 미발행 메시지를 Kafka로 보낸다 (ProductOutboxKafkaRelayScheduler가 1초마다 부른다). 보낸 건수를 돌려준다
	@Transactional
	public int relayKafkaOutbox() {
		return productOutboxKafkaRelayUseCase.relay(LocalDateTime.now());
	}

	// 재시도를 모두 실패해 DLT로 온 메시지를 실패 테이블에 남기고, 커밋 뒤 Slack으로 알린다 (ProductKafkaDltConsumer가 부른다)
	// 같은 메시지는 행 하나로 상태만 바꾼다. 저장이 롤백되면 알림도 보내지 않는다
	@Transactional
	public void recordDlt(KafkaDltMessage message) {
		productDltUseCase.record(message);
	}

	// DLT 실패 행 목록 (내부 API). 최근 것부터 size건
	public List<DltKafkaEventDto> findDlts(DltKafkaEventStatus status, int size) {
		return productDltUseCase.findByStatus(status, size).stream().map(DltKafkaEventDto::from).toList();
	}

	// 사람이 원인을 고친 뒤 DLT 메시지를 첫 재시도 토픽으로 다시 보낸다 (내부 API)
	// ① 처리 실패 → 다시 보냄을 먼저 커밋한다. 보낸 메시지를 본 리스너가 곧바로 처리해도 이 행을 처리 완료로 바꿀 수 있게 하기 위해서다
	// ② 트랜잭션 밖에서 Kafka로 보낸다
	// ③ 보내지 못하면 처리 실패로 되돌려 커밋하고 예외(503)를 그대로 던진다. 브로커가 돌아오면 다시 부르면 된다
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public DltKafkaEventDto resendDlt(long id) {
		ProductDltKafkaEvent event = transactionTemplate.execute(status ->
			productDltUseCase.markResent(id, LocalDateTime.now()));
		try {
			productDltUseCase.send(event);
		} catch (DltKafkaResendException exception) {
			transactionTemplate.executeWithoutResult(status -> productDltUseCase.cancelResent(id));
			throw exception;
		}
		return DltKafkaEventDto.from(event);
	}

	// 사람이 원인을 고친 뒤 메모와 함께 처리 완료로 닫는다 (내부 API)
	@Transactional
	public DltKafkaEventDto completeDlt(long id, String memo) {
		return DltKafkaEventDto.from(productDltUseCase.completeManually(id, memo, LocalDateTime.now()));
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
