package com.bukang.payout.out;

import static java.util.stream.Collectors.toSet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 대회 하나의 판매 내역을 코스 묶음 그대로 들고 있는 것 (주문 응답 BUK-244 모양)
 * 최종 정산 생성은 참가권 목록(List&lt;SoldEntry&gt;)으로 풀지 않고 이 묶음을 돌며 한 장씩 꺼낸다. 목록으로 풀면 20만 장에 SoldEntry 20만 개가 남는다
 */
public record SoldCourses(List<Course> courses) {

	/**
	 * 같은 판매자 · 대회 · 대회일 · 코스 · 가격의 참가권 묶음
	 * blockingIds는 결제 완료가 아닌 참가권(취소 중 · 모름)이다. 하나라도 있으면 그 판매자 × 대회의 최종 정산을 미룬다 (BUK-52)
	 */
	public record Course(Key key, PaidIds paidIds, List<Long> blockingIds) {
		int entryCount() {
			return paidIds.count() + blockingIds.size();
		}
	}

	// 묶음 안 참가권이 모두 같은 값. 참가권 한 장(SoldEntry)은 이 값 + 주문 상품 ID + 상태다
	public record Key(long sellerId, long raceId, LocalDate raceDate, Long raceCourseId, long price) {
		static Key of(SoldEntry entry) {
			return new Key(entry.sellerId(), entry.raceId(), entry.raceDate(), entry.raceCourseId(), entry.price());
		}

		SoldEntry entry(long orderItemId, SoldEntryStatus status) {
			return new SoldEntry(orderItemId, sellerId, raceId, raceCourseId, raceDate, price, status);
		}
	}

	// 참가권 목록을 묶음으로 바꾼다. 테스트 · 가짜 포트처럼 목록으로 판매 내역을 만드는 곳에서 쓴다
	public static SoldCourses of(List<SoldEntry> entries) {
		List<Course> courses = new ArrayList<>();
		groupInOrder(entries, Key::of).forEach((key, group) -> courses.add(new Course(key,
			PaidIds.of(idsOf(group, false)), idsOf(group, true))));
		return new SoldCourses(courses);
	}

	public int entryCount() {
		return courses.stream().mapToInt(Course::entryCount).sum();
	}

	public long blockingCount() {
		return courses.stream().mapToLong(course -> course.blockingIds().size()).sum();
	}

	public Set<LocalDate> raceDates() {
		return courses.stream().map(course -> course.key().raceDate()).collect(toSet());
	}

	// 판매자마다 나눈다. 대회 하나는 판매자 한 명이 정책이지만, 목록으로 만든 판매 내역은 여럿일 수 있다
	public Map<Long, SoldCourses> bySeller() {
		Map<Long, SoldCourses> result = new LinkedHashMap<>();
		groupInOrder(courses, course -> course.key().sellerId())
			.forEach((sellerId, sellerCourses) -> result.put(sellerId, new SoldCourses(sellerCourses)));
		return result;
	}

	// 한 장씩 꺼낸다. SoldEntry는 꺼낼 때 만들고 붙잡지 않는다 (코스마다 결제 완료 다음 미루는 참가권)
	public void forEachEntry(Consumer<SoldEntry> action) {
		for (Course course : courses) {
			course.paidIds().forEach(id -> action.accept(course.key().entry(id, SoldEntryStatus.결제_완료)));
			course.blockingIds().forEach(id -> action.accept(course.key().entry(id, SoldEntryStatus.취소_중)));
		}
	}

	// 대사처럼 참가권 목록이 필요한 곳에서 쓴다. 주문 상품 ID 순으로 정렬한다
	public List<SoldEntry> toEntriesSortedById() {
		List<SoldEntry> entries = new ArrayList<>(entryCount());
		forEachEntry(entries::add);
		entries.sort(Comparator.comparingLong(SoldEntry::orderItemId));
		return entries;
	}

	private static List<Long> idsOf(List<SoldEntry> group, boolean blocking) {
		return group.stream()
			.filter(entry -> entry.blocksFinalPayout() == blocking)
			.map(SoldEntry::orderItemId)
			.toList();
	}

	// 처음 나온 순서를 지켜 묶는다
	private static <T, K> Map<K, List<T>> groupInOrder(Collection<T> items, Function<T, K> keyOf) {
		Map<K, List<T>> grouped = new LinkedHashMap<>();
		items.forEach(item -> grouped.computeIfAbsent(keyOf.apply(item), key -> new ArrayList<>()).add(item));
		return grouped;
	}
}
