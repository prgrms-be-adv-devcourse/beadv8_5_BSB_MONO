package com.bukang.product.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.common.shared.race.event.RaceCourseChangedEvent;
import com.bukang.product.domain.exception.RaceChangeNotAllowedException;
import com.bukang.product.dto.RaceCourseSaveRequestDto;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대회 종목 (참가권). 컬럼은 크루 예매 ERD v5의 race_course 표에 피그잼의 나이 제한·키트 포함과 티셔츠 사이즈를 더했다.
 * 크루 몫·개인 몫 정원과 확보·판매 수를 센다. 남은 자리 판정은 이 행 하나의 조건부 UPDATE로 한다.
 * 이미지(대표·소개·코스 안내·키트 이미지, 소개 영상)는 종목에 연결한다. 클래스 이름 RaceCourse가 파일 연결 대상 종류(FileRef refType)가 된다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "race_course")
// 바뀐 컬럼만 UPDATE한다. 판매자가 종목을 수정할 때 정원 변경 API(⑧-3)가 바꾼 종목 상태(OPEN·SOLD_OUT)를 덮어쓰지 않게 한다
@DynamicUpdate
@AttributeOverride(name = "id", column = @Column(name = "race_course_id"))
@AttributeOverride(name = "createDate", column = @Column(name = "created_at", nullable = false, updatable = false))
@AttributeOverride(name = "modifyDate", column = @Column(name = "updated_at", nullable = false))
public class RaceCourse extends BaseIdAndTime {
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "race_id", nullable = false)
	private Race race;

	// 종목명 (10K, 하프 등)
	@Column(nullable = false, length = 50)
	private String name;

	@Enumerated(STRING)
	@Column(name = "course_type", nullable = false, columnDefinition = "varchar(10)")
	private CourseType courseType;

	@Enumerated(STRING)
	@Column(name = "surface", nullable = false, columnDefinition = "varchar(10)")
	private CourseSurface surface;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal distanceKm;

	// 제한 시간(분). 검색용 필요 페이스 = 제한 시간 ÷ 거리
	@Column(nullable = false)
	private int timeLimitMinutes;

	// 참가 나이 (대회 당일 만 나이). 최소는 만 14세보다 낮게 정할 수 없고, 최대가 null이면 상한이 없다
	@Column(nullable = false)
	private int minAge;

	private Integer maxAge;

	// 참가비 (판매 시작 뒤에는 바꾸지 못한다)
	@Column(nullable = false)
	private long price;

	// 참가권에 포함된 키트 금액 (참가권과 함께 판매·취소하고 배송으로만 받는다)
	// 참가비보다 클 수 없고, 판매 시작 뒤에는 바꾸지 못한다
	@ColumnDefault("0")
	@Column(nullable = false)
	private long kitPrice;

	@Column(nullable = false)
	private boolean kitIncluded;

	// 이 종목이 제공하는 기념 티셔츠 사이즈 (작은 사이즈부터, 예: S, M, L, XL). 비어 있으면 티셔츠가 없는 종목이다
	// 세미는 키트 중 티셔츠만 사이즈를 받는다. 최종에서 키트 옵션이 여러 개가 되면 kit_option 테이블로 옮긴다
	// (DB에는 "S,M,L,XL"처럼 한 칸에 두며, 피그잼 ERD kit_option.size_choices와 같은 형식이라 값을 그대로 옮길 수 있다)
	@Convert(converter = TshirtSizeListConverter.class)
	@Column(name = "tshirt_size_option")
	private List<TshirtSize> tshirtSizeOption = List.of();

	// 정원과 확보·판매 수
	// 남은 크루 자리 = crewQuota − movedQuota − crewReserved − crewSold
	// 남은 개인 자리 = individualQuota + movedQuota − individualReserved − individualSold
	// 정원(crewQuota, individualQuota)은 판매자가 등록·수정으로 정한다
	@ColumnDefault("0")
	@Column(nullable = false)
	private int crewQuota;

	@ColumnDefault("0")
	@Column(nullable = false)
	private int individualQuota;

	// 아래 카운터는 정원 변경 API(⑧-3)의 조건부 UPDATE로만 바꾼다 (updatable = false)
	// Hibernate는 엔티티를 저장할 때 기본적으로 모든 컬럼을 UPDATE한다. 카운터를 넣으면, 판매자가 종목을 수정하는 동안
	// 다른 요청이 올린 확보·판매 수를 수정 트랜잭션이 처음 읽은 옛 값으로 덮어써 자리 수가 사라진다
	//   예: 수정 시작 때 확보 2500 → 그 사이 100명 확보(2600) → 수정 저장이 2500으로 덮어씀 → 100자리가 다시 팔린다
	// 그래서 엔티티 저장(UPDATE 문)에서는 카운터를 빼고, 처음 저장(INSERT)할 때만 0을 넣는다
	@ColumnDefault("0")
	@Column(nullable = false, updatable = false)
	private int crewReserved;

	@ColumnDefault("0")
	@Column(nullable = false, updatable = false)
	private int individualReserved;

	@ColumnDefault("0")
	@Column(nullable = false, updatable = false)
	private int crewSold;

	@ColumnDefault("0")
	@Column(nullable = false, updatable = false)
	private int individualSold;

	// 크루 기간이 끝나 개인 몫으로 옮긴 크루 자리 수. 늘기만 한다
	@ColumnDefault("0")
	@Column(nullable = false, updatable = false)
	private int movedQuota;

	// 크루 몫을 개인 몫으로 옮긴 일시. null이면 크루 기간 진행 중 (두 번 옮기지 않는 조건)
	@Column(updatable = false)
	private LocalDateTime crewClosedAt;

	@Enumerated(STRING)
	@ColumnDefault("'OPEN'")
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private CourseStatus status;

	public RaceCourse(RaceCourseSaveRequestDto request) {
		this.status = CourseStatus.OPEN;
		apply(request);
	}

	public void update(RaceCourseSaveRequestDto request, boolean saleStarted) {
		// 판매 시작 뒤에는 이미 산 사람이 낸 금액과 그 내역이 바뀌지 않게 참가비·키트 금액을 바꾸지 못한다
		if (saleStarted && request.getPrice() != price) {
			throw new RaceChangeNotAllowedException(withName("판매가 시작된 종목은 참가비를 바꿀 수 없습니다."));
		}
		if (saleStarted && request.getKitPrice() != kitPrice) {
			throw new RaceChangeNotAllowedException(withName("판매가 시작된 종목은 키트 금액을 바꿀 수 없습니다."));
		}
		// 판매 시작 뒤에는 정원을 몫별로 늘리기만 한다
		// 줄이는 수정은 같은 순간 들어온 자리 확보와 겹치면, 확인한 뒤 저장하는 사이에 확보된 자리 때문에
		// 정원이 확보·판매된 자리보다 작아질 수 있다 (예: 정원 3000·판매 2500에서 2600으로 줄이는 동안 100명 넘게 확보)
		// 늘리기만 하면 "확보 + 판매 ≤ 정원"(ERD v5 남은 자리 식)이 깨지지 않는다
		// 판매 시작 전에는 확보·판매가 0이라 줄여도 된다
		if (saleStarted && (request.getCrewQuota() < crewQuota || request.getIndividualQuota() < individualQuota)) {
			throw new RaceChangeNotAllowedException(withName("판매가 시작된 종목은 정원을 줄일 수 없습니다."));
		}
		// 판매 시작 뒤에는 이미 신청한 참가자가 산 참가 조건이 바뀌지 않게 종목 내용을 바꾸지 못한다 (종목명은 표기라 허용)
		if (saleStarted) {
			String changedCondition = changedCondition(request);
			if (changedCondition != null) {
				throw new RaceChangeNotAllowedException(
					withName("판매가 시작된 종목은 참가 조건(%s)을 바꿀 수 없습니다.".formatted(changedCondition)));
			}
		}
		// 판매 시작 뒤에는 참가자가 이미 고른 사이즈가 목록에서 빠질 수 있어 티셔츠 사이즈를 바꾸지 못한다
		if (saleStarted && !tshirtSizeOption.equals(sortedTshirtSizes(request))) {
			throw new RaceChangeNotAllowedException(withName("판매가 시작된 종목은 티셔츠 사이즈를 바꿀 수 없습니다."));
		}
		apply(request);
	}

	// 남은 자리 (ERD v5 race_course 주석의 식). 크루 몫을 개인 몫으로 옮기면(movedQuota) 크루 자리가 줄고 개인 자리가 는다
	public int individualRemaining() {
		return individualQuota + movedQuota - individualReserved - individualSold;
	}

	public int crewRemaining() {
		return crewQuota - movedQuota - crewReserved - crewSold;
	}

	// 종목을 빼는 것은 정원을 0으로 줄이는 것과 같으므로, 판매 시작 뒤에는 뺄 수 없다
	public void checkRemovable(boolean saleStarted) {
		if (saleStarted) {
			throw new RaceChangeNotAllowedException(withName("판매가 시작된 대회는 종목을 뺄 수 없습니다."));
		}
	}

	// 대기열에 보낼 종목 변경 메시지 (어느 대회의 종목인지와 수정 일시)
	public RaceCourseChangedEvent toChangedEvent() {
		return new RaceCourseChangedEvent(getId(), race.getId(), getModifyDate(), false);
	}

	// 대회에서 뺀 종목의 삭제 메시지. 지운 행에는 수정 일시가 남지 않으므로 삭제 시각을 받아 담는다
	public RaceCourseChangedEvent toDeletedEvent(LocalDateTime deletedAt) {
		return new RaceCourseChangedEvent(getId(), race.getId(), deletedAt, true);
	}

	// 바뀐 참가 조건의 이름을 돌려준다 (바뀐 것이 없으면 null). 거리는 10과 10.00을 같게 본다
	private String changedCondition(RaceCourseSaveRequestDto request) {
		if (request.getDistanceKm().compareTo(distanceKm) != 0) {
			return "거리";
		}
		if (request.getCourseType() != courseType) {
			return "종목 구분";
		}
		if (request.getSurface() != surface) {
			return "노면";
		}
		if (request.getTimeLimitMinutes() != timeLimitMinutes) {
			return "제한 시간";
		}
		if (request.getMinAge() != minAge || !Objects.equals(request.getMaxAge(), maxAge)) {
			return "나이 제한";
		}
		if (request.getKitIncluded() != kitIncluded) {
			return "키트 포함 여부";
		}
		return null;
	}

	private String withName(String message) {
		return "%s (종목: %s)".formatted(message, name);
	}

	// 판매자가 입력하는 칸을 요청 값으로 채운다. 확보·판매 수와 상태는 정원 변경 API(⑧-3)가 바꾼다
	private void apply(RaceCourseSaveRequestDto request) {
		this.name = request.getName();
		this.courseType = request.getCourseType();
		this.surface = request.getSurface();
		this.distanceKm = request.getDistanceKm();
		this.timeLimitMinutes = request.getTimeLimitMinutes();
		this.minAge = request.getMinAge();
		this.maxAge = request.getMaxAge();
		this.price = request.getPrice();
		this.kitPrice = request.getKitPrice();
		this.kitIncluded = request.getKitIncluded();
		this.tshirtSizeOption = sortedTshirtSizes(request);
		this.crewQuota = request.getCrewQuota();
		this.individualQuota = request.getIndividualQuota();
	}

	// 요청 순서와 상관없이 작은 사이즈부터 둔다 (enum 선언 순서). 그래서 순서만 다른 요청은 같은 목록이 된다
	private List<TshirtSize> sortedTshirtSizes(RaceCourseSaveRequestDto request) {
		return request.getTshirtSizeOption().stream().sorted().toList();
	}

	public void addRace(Race race) {
		this.race = race;
	}
}
