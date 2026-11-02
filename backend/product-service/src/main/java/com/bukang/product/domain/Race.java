package com.bukang.product.domain;

import static jakarta.persistence.CascadeType.PERSIST;
import static jakarta.persistence.CascadeType.REMOVE;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.race.event.RaceChangedEvent;
import com.bukang.product.domain.exception.InvalidRaceException;
import com.bukang.product.domain.exception.RaceChangeNotAllowedException;
import com.bukang.product.dto.RaceSaveRequestDto;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자가 등록한 대회 (상품). 컬럼은 크루 예매 ERD v5의 race 표를 따른다.
 * 종목(참가권)과 정원, 이미지는 RaceCourse에 있다. 대회에 이미지를 붙일지는 최종 프로젝트에서 정한다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "race", uniqueConstraints = {
	// 같은 판매자가 같은 등록 요청을 두 번 보내도 대회는 하나 (PRO-228)
	@UniqueConstraint(name = "uk_race_idempotency", columnNames = {"seller_id", "idempotency_key"})
})
// 바뀐 컬럼만 UPDATE한다. 판매자가 수정하는 동안 상태 배치(RaceSaleStatusScheduler)가 바꾼 판매 상태를
// 수정 트랜잭션이 처음 읽은 옛 상태로 덮어쓰지 않게 하려는 것이다 (수정은 판매 상태를 바꾸지 않는다)
@DynamicUpdate
@AttributeOverride(name = "id", column = @Column(name = "race_id"))
@AttributeOverride(name = "createDate", column = @Column(name = "created_at", nullable = false, updatable = false))
@AttributeOverride(name = "modifyDate", column = @Column(name = "updated_at", nullable = false))
public class Race extends BaseIdAndTime {
	// 판매 시작일은 오늘로부터 이 날수 뒤부터 정할 수 있다 (등록한 뒤 하루 이상 판매 예정으로 둔다)
	private static final int SALE_OPEN_MIN_DAYS = 2;
	// 응답 메시지에 보여 주는 날짜 형식 (예: 10월 16일)
	private static final DateTimeFormatter MESSAGE_DATE = DateTimeFormatter.ofPattern("M월 d일");

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "seller_id", nullable = false)
	private ProductSeller seller;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(nullable = false)
	private String location;

	// 검색 필터. 등록 때 주소 검색 API로 채운다 (시·군·구는 이름이 겹쳐 시·도와 함께 쓴다)
	@Column(nullable = false, length = 20)
	private String regionSido;

	@Column(nullable = false, length = 30)
	private String regionSigungu;

	@Column(nullable = false)
	private LocalDateTime raceDate;

	// 판매 시작일. 판매자가 입력하지 않고 크루 예매 시작·개인 예매 오픈 중 이른 날짜로 채운다
	// 이날 0시에 상태 배치(RaceSaleStatusScheduler)가 판매 중으로 바꾸고, 판매 시작 뒤 수정 규칙도 이날부터 걸린다
	// 구매자가 실제로 예매할 수 있는 시각은 크루 예매 시작·개인 예매 오픈 일시다
	@Column(nullable = false)
	private LocalDate saleOpenDate;

	// 크루 예매 기간. 시작 일시가 null이면 크루 모집 없이 개인 예매만 한다
	private LocalDateTime crewSaleStartAt;

	private LocalDateTime crewSaleEndAt;

	@Column(nullable = false)
	private LocalDateTime individualOpenAt;

	// 접수 마감 일시 (참가자 정보 수정 기한)
	@Column(nullable = false)
	private LocalDateTime applicationCloseAt;

	// 환불 판정 기준일: 이날까지 취소하면 전액 환불, 지나면 환불 불가
	@Column(nullable = false)
	private LocalDate refundDeadline;

	// 화면에 보여 주는 취소·환불 안내 글 (판정은 refundDeadline으로 한다)
	@Column(columnDefinition = "TEXT")
	private String refundPolicy;

	// 크루 참가 신청 인원. 최소는 5 이상만 정할 수 있고, 최대가 null이면 상한이 없다
	@ColumnDefault("5")
	@Column(nullable = false)
	private int crewMinCount;

	private Integer crewMaxCount;

	@Enumerated(STRING)
	@ColumnDefault("'SCHEDULED'")
	@Column(name = "sale_status", nullable = false, columnDefinition = "varchar(20)")
	private RaceSaleStatus saleStatus;

	// 일정·장소 변경 안내 일시 (판매 시작 뒤 대회 일시나 장소를 바꾼 일시. 이날부터 7일 동안은 환불 기준일과 상관없이 전액 환불)
	private LocalDateTime scheduleChangedAt;

	private LocalDateTime canceledAt;

	// 등록 중복 방지 키 (브라우저가 만든 Idempotency-Key 헤더). 같은 판매자가 같은 키로 다시 등록하면 처음 대회를 돌려준다
	// 키를 받기 전에 등록된 대회는 값이 없어 null을 허용한다 (MySQL 유니크 제약은 null끼리 겹쳐도 막지 않는다)
	@Column(length = 64)
	private String idempotencyKey;

	@OneToMany(mappedBy = "race", cascade = {PERSIST, REMOVE}, orphanRemoval = true)
	private List<RaceCourse> courses = new ArrayList<>();

	public Race(ProductSeller seller, RaceSaveRequestDto request, String idempotencyKey, LocalDateTime now) {
		this.seller = seller;
		this.idempotencyKey = idempotencyKey;
		apply(request);
		// 등록하면 판매 예정이고, 판매 시작일 0시에 상태 배치(RaceSaleStatusScheduler)가 판매 중으로 바꾼다
		checkSaleOpenDate(saleOpenDate, now);
		this.saleStatus = RaceSaleStatus.SCHEDULED;
	}

	public void update(RaceSaveRequestDto request, boolean saleStarted, LocalDateTime now) {
		if (saleStatus == RaceSaleStatus.CANCELED) {
			throw new RaceChangeNotAllowedException("취소된 대회는 수정할 수 없습니다.");
		}
		// 판매 종료는 대회일이 지나 완전히 끝난 대회라 어떤 칸도 바꾸지 못한다
		if (isSaleEnded(now)) {
			throw new RaceChangeNotAllowedException("판매가 끝난 대회는 수정할 수 없습니다.");
		}
		// 판매 시작 뒤에는 판매 시작일을 정하는 일정을 바꾸지 못한다
		// 상태 배치는 판매 중을 판매 예정으로 되돌리지 않으므로, 판매 중인데 판매 시작일이 미래인 대회가 생기지 않게 한다
		if (saleStarted) {
			checkSaleOpenScheduleUnchanged(request);
		}
		// 판매 시작 뒤에는 환불 기준일을 늦추는 변경만 된다 (구매자에게 유리한 변경만, ERD refund_deadline 주석)
		if (saleStarted && request.getRefundDeadline().isBefore(refundDeadline)) {
			throw new RaceChangeNotAllowedException("판매가 시작된 대회는 환불 기준일을 앞당길 수 없습니다.");
		}
		// 판매 일정은 마음대로 바꾸지 못한다 (대기열, 크루 마감 다음 날 06:00 배치, 확보 기한이 이 일정을 따른다)
		// 판매 시작일 전에는 일정이 모두 아직 오지 않았으므로, 지난 시각으로 옮기는 것만 막힌다
		checkScheduleChange("크루 예매 시작", crewSaleStartAt, request.getCrewSaleStartAt(), now);
		checkScheduleChange("크루 예매 마감", crewSaleEndAt, request.getCrewSaleEndAt(), now);
		checkScheduleChange("개인 예매 오픈", individualOpenAt, request.getIndividualOpenAt(), now);
		checkScheduleChange("접수 마감", applicationCloseAt, request.getApplicationCloseAt(), now);
		// 판매 시작 뒤 대회 일시(앞당기기·늦추기 모두)나 장소가 바뀌면 일정·장소 변경 안내 일시를 기록한다
		// 이날부터 7일 동안은 환불 기준일과 상관없이 전액 환불된다 (ERD schedule_changed_at, 환불 판정은 주문 쪽)
		// 장소도 참가 여부를 다시 생각하게 하는 변경이라 대회 일시와 같은 기준으로 환불한다
		// 다시 바꾸면 마지막으로 바꾼 일시로 다시 7일을 센다
		// 환불 기준일은 앞당길 수 없고 대회 날짜보다 늦을 수 없으므로, 대회일은 환불 기준일보다 앞으로는 당길 수 없다
		if (saleStarted && (!request.getRaceDate().equals(raceDate) || isPlaceChanged(request))) {
			this.scheduleChangedAt = now;
		}
		// 판매 시작 전 수정으로 판매 시작일이 바뀌면 등록과 같은 기준으로 본다
		LocalDate requestedSaleOpenDate = saleOpenDateOf(request.getCrewSaleStartAt(), request.getIndividualOpenAt());
		if (!requestedSaleOpenDate.equals(saleOpenDate)) {
			checkSaleOpenDate(requestedSaleOpenDate, now);
		}
		apply(request);
	}

	// 판매 예정으로 하루 이상 두도록, 판매 시작일은 오늘로부터 이틀 뒤부터 정할 수 있다
	//   예: 10/14 등록 → 판매 시작일(크루 예매 시작·개인 예매 오픈 중 이른 날짜)이 10/16 이후
	private static void checkSaleOpenDate(LocalDate openDate, LocalDateTime now) {
		LocalDate earliest = now.toLocalDate().plusDays(SALE_OPEN_MIN_DAYS);
		if (openDate.isBefore(earliest)) {
			throw new InvalidRaceException(("판매 시작일은 오늘로부터 이틀 뒤(%s)부터 정할 수 있습니다. "
				+ "크루 예매 시작·개인 예매 오픈 일시를 확인해주세요.").formatted(earliest.format(MESSAGE_DATE)));
		}
	}

	// 판매 시작일을 정하는 일정: 크루 예매가 있으면 크루 예매 시작(개인 예매 오픈보다 늘 이르다), 없으면 개인 예매 오픈
	private void checkSaleOpenScheduleUnchanged(RaceSaveRequestDto request) {
		if (crewSaleStartAt != null && !crewSaleStartAt.equals(request.getCrewSaleStartAt())) {
			throw new RaceChangeNotAllowedException("판매가 시작된 대회는 크루 예매 시작 일시를 바꿀 수 없습니다.");
		}
		if (crewSaleStartAt == null && !individualOpenAt.equals(request.getIndividualOpenAt())) {
			throw new RaceChangeNotAllowedException("판매가 시작된 대회는 개인 예매 오픈 일시를 바꿀 수 없습니다.");
		}
	}

	// 장소는 화면에 보이는 주소와 검색 필터(시·도, 시·군·구) 중 하나라도 바뀌면 바뀐 것으로 본다
	private boolean isPlaceChanged(RaceSaveRequestDto request) {
		return !Objects.equals(location, request.getLocation())
			|| !Objects.equals(regionSido, request.getRegionSido())
			|| !Objects.equals(regionSigungu, request.getRegionSigungu());
	}

	// 이미 지난 일정은 바꿀 수 없고, 아직 오지 않은 일정도 지난 시각으로 옮길 수 없다
	//   예: 지금 10/16, 크루 예매 시작 10/13(지남) → 바꿀 수 없음 / 개인 예매 오픈 10/20 → 10/22로는 되고 10/15로는 안 됨
	private static void checkScheduleChange(String name, LocalDateTime current, LocalDateTime requested,
		LocalDateTime now) {
		if (Objects.equals(current, requested)) {
			return;
		}
		if (current != null && !current.isAfter(now)) {
			throw new RaceChangeNotAllowedException("이미 지난 %s 일시는 바꿀 수 없습니다.".formatted(name));
		}
		if (requested != null && !requested.isAfter(now)) {
			throw new RaceChangeNotAllowedException("%s 일시를 지난 시각으로 바꿀 수 없습니다.".formatted(name));
		}
	}

	// 판매자가 입력하는 칸을 요청 값으로 채운다
	private void apply(RaceSaveRequestDto request) {
		this.title = request.getTitle();
		this.description = request.getDescription();
		this.location = request.getLocation();
		this.regionSido = request.getRegionSido();
		this.regionSigungu = request.getRegionSigungu();
		this.raceDate = request.getRaceDate();
		this.crewSaleStartAt = request.getCrewSaleStartAt();
		this.crewSaleEndAt = request.getCrewSaleEndAt();
		this.individualOpenAt = request.getIndividualOpenAt();
		this.applicationCloseAt = request.getApplicationCloseAt();
		this.refundDeadline = request.getRefundDeadline();
		this.refundPolicy = request.getRefundPolicy();
		this.crewMinCount = request.getCrewMinCount();
		this.crewMaxCount = request.getCrewMaxCount();
		this.saleOpenDate = saleOpenDateOf(request.getCrewSaleStartAt(), request.getIndividualOpenAt());
	}

	// 크루 예매 시작·개인 예매 오픈 중 이른 날짜. 크루 예매가 없으면(시작 일시 null) 개인 예매 오픈 날짜다
	private static LocalDate saleOpenDateOf(LocalDateTime crewSaleStartAt, LocalDateTime individualOpenAt) {
		if (crewSaleStartAt != null && crewSaleStartAt.isBefore(individualOpenAt)) {
			return crewSaleStartAt.toLocalDate();
		}
		return individualOpenAt.toLocalDate();
	}

	public void addRaceCourse(RaceCourse raceCourse) {
		courses.add(raceCourse);
		raceCourse.addRace(this);
	}

	// 목록에서 빼면 orphanRemoval로 종목 행이 지워진다
	public void removeRaceCourse(RaceCourse raceCourse) {
		courses.remove(raceCourse);
	}

	// 종목 목록을 요청 순서에 맞춘다 (응답의 종목 ID 순서). 순서 컬럼이 없어 DB에는 영향이 없다
	public void sortCourses(List<RaceCourse> requestOrder) {
		courses.sort(Comparator.comparingInt(requestOrder::indexOf));
	}

	public boolean isOwnedBy(ProductSeller seller) {
		return this.seller.getId() == seller.getId();
	}

	// 대회는 등록하면 누구나 볼 수 있다. 취소된 대회만 등록한 판매자(회원)만 볼 수 있다
	// 취소된 대회는 자동 환불로 끝난 대회라 공개 화면에서 뺀다 (판매자는 내 대회에서 기록을 본다)
	public boolean isVisibleTo(Long memberId) {
		return saleStatus != RaceSaleStatus.CANCELED || (memberId != null && seller.getMember().getId() == memberId);
	}

	// 판매 시작일 0시가 지났으면 판매가 시작된 대회다 (상태 배치가 판매 중으로 바꾸는 기준과 같다)
	// 판매 시작 뒤 수정 규칙(참가비·정원·참가 조건 고정 등)이 이때부터 걸린다
	public boolean isSaleStarted(LocalDateTime now) {
		return !now.toLocalDate().isBefore(saleOpenDate);
	}

	// 판매 종료 전이면 어떤 상태든 취소할 수 있다 (10/13 결정)
	public void checkCancelable(LocalDateTime now) {
		if (saleStatus == RaceSaleStatus.CANCELED) {
			throw new RaceChangeNotAllowedException("이미 취소된 대회입니다.");
		}
		if (isSaleEnded(now)) {
			throw new RaceChangeNotAllowedException("판매가 끝난 대회는 취소할 수 없습니다.");
		}
	}

	// 대회일 다음 날 0시부터는 판매 종료로 본다
	// (상태 배치가 판매 종료로 바꾸기 전이라도 같은 기준으로 막는다)
	private boolean isSaleEnded(LocalDateTime now) {
		return saleStatus == RaceSaleStatus.CLOSED || raceDate.isBefore(now.toLocalDate().atStartOfDay());
	}

	// 개인 예매로 자리를 확보할 수 있는지: 판매 예정·판매 중이고, 개인 예매 오픈부터 접수 마감 전까지
	// 판매 예정도 넣는 이유: 판매 시작일 0시가 지나도 상태 배치가 판매 중으로 바꾸기까지 최대 5분 걸린다
	// (개인 예매가 판매 시작일 0시에 열리는 대회)
	public boolean isAcceptingIndividual(LocalDateTime now) {
		return (saleStatus == RaceSaleStatus.SCHEDULED || saleStatus == RaceSaleStatus.ON_SALE)
			&& !now.isBefore(individualOpenAt) && now.isBefore(applicationCloseAt);
	}

	// 대기열에 보낼 대회 변경 메시지 (진입 판정에 쓰는 칸과 수정 일시)
	public RaceChangedEvent toChangedEvent() {
		return new RaceChangedEvent(getId(), saleStatus, individualOpenAt, applicationCloseAt, getModifyDate());
	}
}
