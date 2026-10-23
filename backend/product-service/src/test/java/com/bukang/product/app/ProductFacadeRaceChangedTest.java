package com.bukang.product.app;

import static com.bukang.product.support.RaceRequests.course;
import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.race.event.RaceChangedEvent;
import com.bukang.product.domain.Race;
import com.bukang.product.dto.RaceSaveDto;
import com.bukang.product.support.ProductIntegrationTest;
import com.bukang.product.support.RaceRequests.RaceBuilder;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * ProductFacade가 대회를 바꾸면서 대기열 서비스에 보낼 대회 변경 메시지(queuing.race.changed)를 아웃박스에 남기는지 확인한다.
 * - 등록·수정·취소는 대회 ID를 key로 1건을 남기고, 상태 배치는 판매 상태를 바꾼 대회마다 1건을 남긴다
 * - 메시지에는 대기열의 진입 판정에 쓰는 칸(판매 상태, 개인 예매 오픈, 접수 마감)과 대회 행의 수정 일시를 담는다
 *
 * 메시지를 확인하는 방법 (Kafka 브로커 없이)
 * 대회 저장과 같은 트랜잭션에서 아웃박스(PRODUCT_OUTBOX_EVENT)에 남긴 행을 읽는다. 테스트에서는 릴레이가 돌지 않아 보내지 않는다.
 * 1. key 확인: raceChangedKeys()가 queuing.race.changed 행의 key(ref_id = 대회 ID)만 꺼낸다
 * 2. 본문 확인: raceChangedMessages()·lastRaceChanged()가 같은 행의 본문(payload)을 꺼낸다
 * 아웃박스는 테스트마다 비워지므로, 각 테스트는 그 테스트 안에서 남긴 메시지만 센다.
 * Kafka로 보내는 일은 ProductOutboxRelayTest가 확인한다.
 */
class ProductFacadeRaceChangedTest extends ProductIntegrationTest {
	// 등록 요청과 수정 요청이 같은 일정을 쓰도록 기준 시각을 테스트마다 하나로 고정한다
	private final LocalDateTime now = LocalDateTime.now().withNano(0);

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@DisplayName("registerRace: 대회를 등록하면 queuing.race.changed로 대회 ID를 key로 1건을 보내고, "
		+ "판매 예정(SCHEDULED)·개인 예매 오픈(이틀 뒤)·접수 마감(10일 뒤)·대회 행의 수정 일시 5개 칸만 담는다")
	void publishRaceChangedOnRegister() {
		RaceSaveDto saved = productFacade.registerRace(SELLER_MEMBER_ID, newIdempotencyKey(), scheduledRace().build())
			.race();

		// queuing.race.changed로 보낸 메시지는 1건이고, 그 key는 등록된 대회 ID다
		assertThat(raceChangedKeys()).containsExactly(String.valueOf(saved.getRaceId()));
		// 대기열이 읽는 칸 이름 그대로, 판매 상태는 enum 이름으로 보냈는지 본문을 직접 읽어 확인한다
		Map<String, Object> body = JsonPath.read(raceChangedMessages().getFirst(), "$");
		assertThat(body)
			.containsOnlyKeys("raceId", "saleStatus", "individualOpenAt", "applicationCloseAt", "updatedAt");
		assertThat(body).containsEntry("saleStatus", "SCHEDULED");

		RaceChangedEvent event = lastRaceChanged();
		assertThat(event.raceId()).isEqualTo(saved.getRaceId());
		assertThat(event.saleStatus()).isEqualTo(RaceSaleStatus.SCHEDULED);
		assertThat(event.individualOpenAt()).isEqualTo(now.plusDays(2));
		assertThat(event.applicationCloseAt()).isEqualTo(now.plusDays(10));
		// 등록 메시지는 DB에서 다시 읽지 않은 엔티티의 시각이라, DB가 소수 6자리로 맞춘 값과 1밀리초 안에서 같다
		assertThat(event.updatedAt())
			.isCloseTo(findRace(saved.getRaceId()).getModifyDate(), within(1, ChronoUnit.MILLIS));
	}

	@Test
	@DisplayName("updateRace: 판매 예정 대회의 접수 마감을 10일 뒤에서 9일 뒤로 수정하면 queuing.race.changed로 1건을 더 보내고, "
		+ "새 접수 마감과 수정한 시각의 수정 일시를 담는다")
	void publishRaceChangedOnUpdate() throws Exception {
		// 등록 한번
		RegisteredRace registered = register(scheduledRace().build());
		RaceChangedEvent registeredEvent = lastRaceChanged();
		// 수정 한번
		productFacade.updateRace(SELLER_MEMBER_ID, registered.raceId(), scheduledRace()
			.applicationCloseAt(now.plusDays(9))
			.course(course().courseId(registered.courseId()))
			.build());

		// 등록 때 1건, 수정 때 1건. 둘 다 key는 대회 ID다
		String raceKey = String.valueOf(registered.raceId());
		assertThat(raceChangedKeys()).containsExactly(raceKey, raceKey); // key가 2번 나갔냐? 확인
		Race updated = findRace(registered.raceId());
		assertThat(lastRaceChanged()).isEqualTo(new RaceChangedEvent(registered.raceId(), RaceSaleStatus.SCHEDULED,
			now.plusDays(2), now.plusDays(9), updated.getModifyDate()));
		assertThat(updated.getModifyDate()).isAfter(registeredEvent.updatedAt());
	}

	@Test
	@DisplayName("cancelRace: 판매 중인 대회를 취소하면 queuing.race.changed로 1건을 더 보내고, "
		+ "대회 취소(CANCELED)와 취소 일시와 같은 수정 일시를 담는다")
	void publishRaceChangedOnCancel() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());

		productFacade.cancelRace(SELLER_MEMBER_ID, onSale.raceId());

		// 등록 때 1건, 취소 때 1건. 둘 다 key는 대회 ID다
		// (registerOnSale은 DB를 직접 고쳐 판매 중으로 바꾸므로 메시지가 없다)
		String raceKey = String.valueOf(onSale.raceId());
		assertThat(raceChangedKeys()).containsExactly(raceKey, raceKey);
		Race canceled = findRace(onSale.raceId());
		assertThat(canceled.getModifyDate()).isEqualTo(canceled.getCanceledAt());
		assertThat(lastRaceChanged()).isEqualTo(new RaceChangedEvent(onSale.raceId(), RaceSaleStatus.CANCELED,
			canceled.getIndividualOpenAt(), canceled.getApplicationCloseAt(), canceled.getModifyDate()));
	}

	@Test
	@DisplayName("refreshSaleStatuses: 판매 시작일이 된 판매 예정 대회는 상태 배치를 돌리면 queuing.race.changed로 1건을 더 보내고, "
		+ "판매 중(ON_SALE)과 배치가 바꾼 수정 일시를 담는다")
	void publishRaceChangedOnSaleStatusRefresh() throws Exception {
		RegisteredRace registered = register(scheduledRace().build());
		// 기다리지 않고 판매 시작일이 된 상태를 만들려고 판매 시작일만 오늘로 옮긴다 (DB를 직접 고쳐 메시지가 없다)
		updateSaleOpenDate(registered.raceId(), LocalDate.now());

		productFacade.refreshSaleStatuses();

		// 등록 때 1건, 상태 배치 때 1건. 둘 다 key는 대회 ID다
		String raceKey = String.valueOf(registered.raceId());
		assertThat(raceChangedKeys()).containsExactly(raceKey, raceKey);
		Race opened = findRace(registered.raceId());
		assertThat(lastRaceChanged()).isEqualTo(new RaceChangedEvent(registered.raceId(), RaceSaleStatus.ON_SALE,
			now.plusDays(2), now.plusDays(10), opened.getModifyDate()));
	}

	// 판매 예정 대회의 일정 (대회 15일 뒤, 개인 예매 이틀 뒤 오픈, 접수 마감 10일 뒤)
	// 판매 시작일이 오늘로부터 이틀 뒤라 register()로 등록된다
	private RaceBuilder scheduledRace() {
		return race()
			.raceDate(now.plusDays(15))
			.individualOpenAt(now.plusDays(2))
			.applicationCloseAt(now.plusDays(10));
	}

	private Race findRace(int raceId) {
		return raceRepository.findById(raceId).orElseThrow();
	}

	private List<String> raceChangedMessages() {
		return outboxPayloads(KafkaTopics.RACE_CHANGED);
	}

	private List<String> raceChangedKeys() {
		return outboxKeys(KafkaTopics.RACE_CHANGED);
	}

	private RaceChangedEvent lastRaceChanged() {
		return jsonConverter.fromJson(raceChangedMessages().getLast(), RaceChangedEvent.class);
	}

	// API로는 만들 수 없는 판매 시작일(오늘)을 만들려고 대회 행을 직접 고친다 (메시지를 보내지 않는다)
	private void updateSaleOpenDate(int raceId, LocalDate saleOpenDate) {
		transactionTemplate.executeWithoutResult(status -> entityManager
			.createQuery("update Race r set r.saleOpenDate = :saleOpenDate where r.id = :id")
			.setParameter("saleOpenDate", saleOpenDate)
			.setParameter("id", raceId)
			.executeUpdate());
	}
}
