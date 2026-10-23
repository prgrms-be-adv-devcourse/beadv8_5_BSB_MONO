package com.bukang.product.app;

import static com.bukang.product.support.RaceRequests.course;
import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.race.event.RaceCourseChangedEvent;
import com.bukang.product.domain.CourseType;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.dto.RaceSaveDto;
import com.bukang.product.support.ProductIntegrationTest;
import com.bukang.product.support.RaceRequests.CourseBuilder;
import com.bukang.product.support.RaceRequests.RaceBuilder;
import com.jayway.jsonpath.JsonPath;

/**
 * ProductFacade가 대회를 저장하면서 대기열 서비스에 보낼 종목 변경 메시지(queuing.racecourse.changed)를 아웃박스에 남기는지 확인한다.
 * - 등록은 종목마다 1건, 수정은 바뀐 종목·새 종목·뺀 종목마다 1건을 남긴다. key는 모두 대회 ID다
 * - 뺀 종목은 deleted=true와 삭제 시각(수정을 시작한 시각)을 담는다
 * - 취소는 종목 메시지를 남기지 않는다 (대회 메시지만 남긴다)
 *
 * 메시지는 ProductFacadeRaceChangedTest와 같은 방법으로 확인한다.
 * 아웃박스(PRODUCT_OUTBOX_EVENT)에 남은 이 토픽의 행에서 key(ref_id)와 본문(payload)을 꺼낸다.
 * 등록할 때도 종목 메시지가 남으므로, 수정 테스트는 등록 때 남긴 건수를 세어 두고 그 뒤에 남긴 메시지만 본다.
 */
class ProductFacadeRaceCourseChangedTest extends ProductIntegrationTest {
	// 등록 요청과 수정 요청이 같은 일정을 쓰도록 기준 시각을 테스트마다 하나로 고정한다
	private final LocalDateTime now = LocalDateTime.now().withNano(0);

	@Test
	@DisplayName("registerRace: 종목 2개(10K·하프)로 대회를 등록하면 queuing.racecourse.changed로 종목마다 1건씩 2건을 보낸다. "
		+ "key는 둘 다 대회 ID이고, 본문은 종목 ID·대회 ID·종목 행의 수정 일시·deleted=false 4개 칸만 담는다")
	void publishCourseChangedOnRegister() {
		RaceSaveDto saved = productFacade.registerRace(SELLER_MEMBER_ID, newIdempotencyKey(),
			scheduledRace().course(tenK(), half()).build()).race();

		String raceKey = String.valueOf(saved.getRaceId());
		assertThat(raceCourseChangedKeys()).containsExactly(raceKey, raceKey);
		// 대기열이 읽는 칸 이름 그대로 보냈는지 본문을 직접 읽어 확인한다
		Map<String, Object> body = JsonPath.read(raceCourseChangedMessages().getFirst(), "$");
		assertThat(body).containsOnlyKeys("raceCourseId", "raceId", "updatedAt", "deleted");

		List<RaceCourseChangedEvent> events = raceCourseChanges();
		// 종목 순서는 요청 순서(응답의 종목 ID 순서)와 같다
		assertThat(events).extracting(RaceCourseChangedEvent::raceCourseId)
			.containsExactlyElementsOf(saved.getCourseIds());
		assertThat(events).allSatisfy(event -> {
			assertThat(event.raceId()).isEqualTo(saved.getRaceId());
			assertThat(event.deleted()).isFalse();
			// 등록 메시지는 DB에서 다시 읽지 않은 엔티티의 시각이라, DB가 소수 6자리로 맞춘 값과 1밀리초 안에서 같다
			assertThat(event.updatedAt())
				.isCloseTo(findCourse(event.raceCourseId()).getModifyDate(), within(1, ChronoUnit.MILLIS));
		});
	}

	@Test
	@DisplayName("updateRace: 종목 2개(10K·하프) 중 10K의 이름만 '10K 코스'로 바꾸면 10K 1건만 더 보내고, "
		+ "손대지 않은 하프는 보내지 않는다. 수정 일시는 DB에 저장된 10K의 새 수정 일시다")
	void publishOnlyChangedCourseOnUpdate() throws Exception {
		RegisteredRace registered = register(scheduledRace().course(tenK(), half()).build());
		int tenKId = registered.courseIds().get(0);
		int halfId = registered.courseIds().get(1);
		int sentOnRegister = raceCourseChanges().size();

		productFacade.updateRace(SELLER_MEMBER_ID, registered.raceId(), scheduledRace()
			.course(tenK().name("10K 코스").courseId(tenKId), half().courseId(halfId))
			.build());

		// 등록 때 2건, 수정 때 1건. 모두 key는 대회 ID다
		String raceKey = String.valueOf(registered.raceId());
		assertThat(raceCourseChangedKeys()).containsExactly(raceKey, raceKey, raceKey);
		// 하프는 바뀐 칸이 없어 수정 일시가 그대로라 보내지 않는다
		assertThat(raceCourseChangesSince(sentOnRegister)).containsExactly(
			new RaceCourseChangedEvent(tenKId, registered.raceId(), findCourse(tenKId).getModifyDate(), false));
	}

	@Test
	@DisplayName("updateRace: 10K만 있는 대회에 하프를 더하면 새 종목(하프) 1건을 더 보내고, 그대로 둔 10K는 보내지 않는다")
	void publishNewCourseOnUpdate() throws Exception {
		RegisteredRace registered = register(scheduledRace().course(tenK()).build());
		int sentOnRegister = raceCourseChanges().size();

		RaceSaveDto updated = productFacade.updateRace(SELLER_MEMBER_ID, registered.raceId(), scheduledRace()
			.course(tenK().courseId(registered.courseId()), half())
			.build());

		// 응답의 종목 ID는 요청 순서대로라 두 번째가 새로 만든 하프다
		int halfId = updated.getCourseIds().get(1);
		// 등록 때 1건, 수정 때 1건. 모두 key는 대회 ID다
		String raceKey = String.valueOf(registered.raceId());
		assertThat(raceCourseChangedKeys()).containsExactly(raceKey, raceKey);
		assertThat(raceCourseChangesSince(sentOnRegister)).containsExactly(
			new RaceCourseChangedEvent(halfId, registered.raceId(), findCourse(halfId).getModifyDate(), false));
	}

	@Test
	@DisplayName("updateRace: 판매 예정 대회의 종목 2개(10K·하프) 중 하프를 빼면 하프 1건을 deleted=true로 더 보내고, "
		+ "수정 일시 칸에는 삭제 시각(수정을 시작한 시각)을 담는다")
	void publishDeletedCourseOnUpdate() throws Exception {
		RegisteredRace registered = register(scheduledRace().course(tenK(), half()).build());
		int tenKId = registered.courseIds().get(0);
		int halfId = registered.courseIds().get(1);
		RaceCourseChangedEvent halfRegistered = raceCourseChanges().get(1);
		int sentOnRegister = raceCourseChanges().size();

		LocalDateTime beforeUpdate = LocalDateTime.now();
		productFacade.updateRace(SELLER_MEMBER_ID, registered.raceId(), scheduledRace()
			.course(tenK().courseId(tenKId))
			.build());
		LocalDateTime afterUpdate = LocalDateTime.now();

		assertThat(raceCourseRepository.existsById(halfId)).isFalse();
		// 등록 때 2건, 수정 때 1건. 모두 key는 대회 ID다
		String raceKey = String.valueOf(registered.raceId());
		assertThat(raceCourseChangedKeys()).containsExactly(raceKey, raceKey, raceKey);
		List<RaceCourseChangedEvent> sent = raceCourseChangesSince(sentOnRegister);
		assertThat(sent).hasSize(1);
		RaceCourseChangedEvent deleted = sent.getFirst();
		assertThat(deleted.raceCourseId()).isEqualTo(halfId);
		assertThat(deleted.raceId()).isEqualTo(registered.raceId());
		assertThat(deleted.deleted()).isTrue();
		// 지운 행에는 수정 일시가 남지 않아, 수정을 시작한 시각을 삭제 시각으로 담는다
		// 그래서 updateRace를 부른 앞뒤 시각 사이이고, 등록 때 보낸 하프 메시지보다 늦다
		assertThat(deleted.updatedAt())
			.isBetween(beforeUpdate, afterUpdate)
			.isAfter(halfRegistered.updatedAt());
	}

	@Test
	@DisplayName("cancelRace: 종목 2개(10K·하프)인 대회를 취소하면 대회 메시지만 1건을 더 보내고, 종목 메시지는 보내지 않는다")
	void noCourseChangedOnCancel() throws Exception {
		RegisteredRace registered = register(scheduledRace().course(tenK(), half()).build());

		productFacade.cancelRace(SELLER_MEMBER_ID, registered.raceId());

		String raceKey = String.valueOf(registered.raceId());
		// 대회 메시지는 등록 때 1건, 취소 때 1건
		assertThat(outboxKeys(KafkaTopics.RACE_CHANGED)).containsExactly(raceKey, raceKey);
		// 종목 메시지는 등록 때 보낸 2건 그대로다
		assertThat(raceCourseChangedKeys()).containsExactly(raceKey, raceKey);
	}

	// 판매 예정 대회의 일정 (대회 15일 뒤, 개인 예매 이틀 뒤 오픈, 접수 마감 10일 뒤)
	// 판매 시작일이 오늘로부터 이틀 뒤라 register()로 등록되고, 판매 시작 전이라 종목을 뺄 수 있다
	private RaceBuilder scheduledRace() {
		return race()
			.raceDate(now.plusDays(15))
			.individualOpenAt(now.plusDays(2))
			.applicationCloseAt(now.plusDays(10));
	}

	private CourseBuilder tenK() {
		return course().name("10K");
	}

	private CourseBuilder half() {
		return course().name("하프").type(CourseType.HALF).distanceKm("21.1").timeLimitMinutes(180);
	}

	private RaceCourse findCourse(int raceCourseId) {
		return raceCourseRepository.findById(raceCourseId).orElseThrow();
	}

	private List<String> raceCourseChangedMessages() {
		return outboxPayloads(KafkaTopics.RACE_COURSE_CHANGED);
	}

	private List<String> raceCourseChangedKeys() {
		return outboxKeys(KafkaTopics.RACE_COURSE_CHANGED);
	}

	// 이 테스트에서 보낸 종목 메시지 전체 (보낸 순서대로)
	private List<RaceCourseChangedEvent> raceCourseChanges() {
		return raceCourseChangedMessages().stream()
			.map(payload -> jsonConverter.fromJson(payload, RaceCourseChangedEvent.class))
			.toList();
	}

	// 앞에서 보낸 sentBefore건을 건너뛰고, 그 뒤에 보낸 종목 메시지만
	private List<RaceCourseChangedEvent> raceCourseChangesSince(int sentBefore) {
		List<RaceCourseChangedEvent> all = raceCourseChanges();
		return all.subList(sentBefore, all.size());
	}
}
