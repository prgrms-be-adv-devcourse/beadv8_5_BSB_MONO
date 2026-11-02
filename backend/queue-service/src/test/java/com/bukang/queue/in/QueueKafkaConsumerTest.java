package com.bukang.queue.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.race.event.RaceChangedEvent;
import com.bukang.common.shared.race.event.RaceCourseChangedEvent;
import com.bukang.queue.domain.QueueRace;
import com.bukang.queue.domain.QueueRaceCourse;
import com.bukang.queue.support.QueueIntegrationTest;

/**
 * QueueKafkaConsumer가 상품 서비스의 대회·종목 변경 메시지를 받아 복제본에 저장하는지 확인한다.
 * 메시지는 상품이 보내는 것과 같은 JSON 문자열(이벤트를 JsonConverter로 바꾼 값)로 만들어 리스너 메서드를 직접 부른다.
 * - 행마다 저장된 수정 일시보다 늦은 메시지만 반영한다 (같으면 같은 메시지가 다시 온 것, 이르면 늦게 도착한 옛 메시지)
 * - 뺀 종목은 행을 지우지 않고 삭제 표시(deleted)만 남겨, 늦게 온 옛 메시지가 되살리지 못하게 한다
 * 재시도·DLT 토픽으로 보내는 일은 Kafka가 하므로, 여기서는 어떤 예외가 나는지까지만 본다.
 */
class QueueKafkaConsumerTest extends QueueIntegrationTest {
	private static final long RACE_ID = 1;
	private static final long RACE_COURSE_ID = 11;
	private static final LocalDateTime INDIVIDUAL_OPEN_AT = LocalDateTime.of(2026, 10, 20, 10, 0);
	private static final LocalDateTime APPLICATION_CLOSE_AT = LocalDateTime.of(2026, 11, 1, 18, 0);

	@Autowired
	private QueueKafkaConsumer queueKafkaConsumer;

	@Test
	@DisplayName("handleRaceChanged: 처음 온 대회 메시지는 대회 ID·판매 상태·개인 예매 오픈·접수 마감·수정 일시를 그대로 복제본에 저장한다")
	void saveNewRace() {
		queueKafkaConsumer.handleRaceChanged(raceMessage(RaceSaleStatus.SCHEDULED, INDIVIDUAL_OPEN_AT, at(10)));
		// usingRecursiveComparison()을 붙이면 id, saleStatus, individualOpenAt, applicationCloseAt, updatedAt 필드를 하나씩 비교
		assertThat(findRace(RACE_ID)).usingRecursiveComparison().isEqualTo(
			new QueueRace(RACE_ID, RaceSaleStatus.SCHEDULED, INDIVIDUAL_OPEN_AT, APPLICATION_CLOSE_AT, at(10)));
	}

	@Test
	@DisplayName("handleRaceChanged: 같은 대회 메시지가 두 번 와도 복제본은 1행이고 값도 처음과 같다 (멱등)")
	void sameRaceMessageTwice() {
		String message = raceMessage(RaceSaleStatus.SCHEDULED, INDIVIDUAL_OPEN_AT, at(10));

		queueKafkaConsumer.handleRaceChanged(message);
		queueKafkaConsumer.handleRaceChanged(message);
		// 두 개가 들어갔다면 List<QueueRace> 로 나오기에 비교에 실패해야함. 그러나 실패를 하지 않는건 멱등하게 하나만 처리되었음을 의미
		assertThat(queueRaceRepository.count()).isEqualTo(1);
		assertThat(findRace(RACE_ID)).usingRecursiveComparison().isEqualTo(
			new QueueRace(RACE_ID, RaceSaleStatus.SCHEDULED, INDIVIDUAL_OPEN_AT, APPLICATION_CLOSE_AT, at(10)));
	}

	@Test
	@DisplayName("handleRaceChanged: 판매자가 개인 예매 오픈을 10/19 → 10/20으로 고친 뒤(10시), 고치기 전의 옛 메시지(9시)가 "
		+ "재시도로 늦게 오면 건너뛰어 10/20이 그대로다")
	void skipOlderRaceMessage() {
		queueKafkaConsumer.handleRaceChanged(raceMessage(RaceSaleStatus.SCHEDULED, INDIVIDUAL_OPEN_AT, at(10)));

		queueKafkaConsumer.handleRaceChanged(
			raceMessage(RaceSaleStatus.SCHEDULED, INDIVIDUAL_OPEN_AT.minusDays(1), at(9)));

		QueueRace race = findRace(RACE_ID);
		assertThat(race.getIndividualOpenAt()).isEqualTo(INDIVIDUAL_OPEN_AT);
		assertThat(race.getUpdatedAt()).isEqualTo(at(10));
	}

	@Test
	@DisplayName("handleRaceChanged: 판매 예정(10시)인 대회에 상태 배치가 보낸 판매 중 메시지(11시)가 오면 판매 상태와 수정 일시가 바뀐다")
	void applyNewerRaceMessage() {
		queueKafkaConsumer.handleRaceChanged(raceMessage(RaceSaleStatus.SCHEDULED, INDIVIDUAL_OPEN_AT, at(10)));

		queueKafkaConsumer.handleRaceChanged(raceMessage(RaceSaleStatus.ON_SALE, INDIVIDUAL_OPEN_AT, at(11)));

		assertThat(findRace(RACE_ID)).usingRecursiveComparison().isEqualTo(
			new QueueRace(RACE_ID, RaceSaleStatus.ON_SALE, INDIVIDUAL_OPEN_AT, APPLICATION_CLOSE_AT, at(11)));
	}

	@Test
	@DisplayName("handleRaceCourseChanged: 처음 온 종목 메시지는 종목 ID·대회 ID·수정 일시와 deleted=false를 복제본에 저장한다")
	void saveNewRaceCourse() {
		queueKafkaConsumer.handleRaceCourseChanged(raceCourseMessage(at(10), false));

		assertThat(findCourse(RACE_COURSE_ID)).usingRecursiveComparison().isEqualTo(
			new QueueRaceCourse(RACE_COURSE_ID, RACE_ID, at(10), false));
	}

	@Test
	@DisplayName("handleRaceCourseChanged: 저장된 종목(10시)을 대회에서 뺀 삭제 메시지(12시)가 오면 행은 남고 deleted=true, "
		+ "수정 일시는 삭제 시각이 된다")
	void markRaceCourseDeleted() {
		queueKafkaConsumer.handleRaceCourseChanged(raceCourseMessage(at(10), false));

		queueKafkaConsumer.handleRaceCourseChanged(raceCourseMessage(at(12), true));

		assertThat(queueRaceCourseRepository.count()).isEqualTo(1);
		assertThat(findCourse(RACE_COURSE_ID)).usingRecursiveComparison().isEqualTo(
			new QueueRaceCourse(RACE_COURSE_ID, RACE_ID, at(12), true));
	}

	@Test
	@DisplayName("handleRaceCourseChanged: 복제본에 없는 종목의 삭제 메시지(12시)가 와도 deleted=true 행을 만든다 "
		+ "(등록 메시지가 재시도에 걸려 아직 오지 않은 경우)")
	void saveDeletedRaceCourseNotStored() {
		queueKafkaConsumer.handleRaceCourseChanged(raceCourseMessage(at(12), true));

		assertThat(findCourse(RACE_COURSE_ID)).usingRecursiveComparison().isEqualTo(
			new QueueRaceCourse(RACE_COURSE_ID, RACE_ID, at(12), true));
	}

	@Test
	@DisplayName("handleRaceCourseChanged: 삭제 메시지(12시)를 먼저 받은 뒤 재시도로 늦게 온 등록 메시지(10시, deleted=false)는 건너뛰어, "
		+ "뺀 종목이 되살아나지 않는다")
	void skipOlderRaceCourseMessageAfterDeleted() {
		queueKafkaConsumer.handleRaceCourseChanged(raceCourseMessage(at(12), true));

		queueKafkaConsumer.handleRaceCourseChanged(raceCourseMessage(at(10), false));

		assertThat(findCourse(RACE_COURSE_ID)).usingRecursiveComparison().isEqualTo(
			new QueueRaceCourse(RACE_COURSE_ID, RACE_ID, at(12), true));
	}

	@Test
	@DisplayName("handleRaceChanged: 모르는 판매 상태 값(OPEN)이 오면 IllegalArgumentException이 나고 복제본에 저장하지 않는다 "
		+ "(재시도 없이 바로 DLT로 보내는 예외)")
	void unknownSaleStatus() {
		String message = """
			{"raceId":1,"saleStatus":"OPEN","individualOpenAt":"2026-10-20T10:00:00",\
			"applicationCloseAt":"2026-11-01T18:00:00","updatedAt":"2026-10-15T10:00:00"}""";

		assertThatThrownBy(() -> queueKafkaConsumer.handleRaceChanged(message))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(queueRaceRepository.count()).isZero();
	}

	// 상품이 보내는 대회 메시지 (접수 마감은 모든 테스트에서 같다)
	private String raceMessage(RaceSaleStatus saleStatus, LocalDateTime individualOpenAt, LocalDateTime updatedAt) {
		return jsonConverter.toJson(
			new RaceChangedEvent(RACE_ID, saleStatus, individualOpenAt, APPLICATION_CLOSE_AT, updatedAt));
	}

	// 상품이 보내는 종목 메시지 (대회 RACE_ID의 종목 RACE_COURSE_ID)
	private String raceCourseMessage(LocalDateTime updatedAt, boolean deleted) {
		return jsonConverter.toJson(new RaceCourseChangedEvent(RACE_COURSE_ID, RACE_ID, updatedAt, deleted));
	}
}
