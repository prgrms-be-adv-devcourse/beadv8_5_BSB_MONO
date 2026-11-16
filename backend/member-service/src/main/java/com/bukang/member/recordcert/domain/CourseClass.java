package com.bukang.member.recordcert.domain;

// 종목 구분: 기록증의 종목과 크루런에서 파는 종목을 같은 묶음으로 부른다. 맞지 않는 거리는 OTHER(기타)
public enum CourseClass {
	FIVE_K(50_000),
	TEN_K(100_000),
	HALF(210_975),
	FULL(421_950),
	// 거리를 몰라 가장 짧은 울트라 거리 대신 풀 거리를 쓴다 (빠른 기록 기준이 가장 느슨해지는 쪽)
	ULTRA(421_950),
	OTHER(0);

	// 거리(0.1m 단위). 하프 21.0975km를 정수로 담으려고 0.1m를 쓴다. OTHER는 거리를 모른다
	private final long decimeters;

	CourseClass(long decimeters) {
		this.decimeters = decimeters;
	}

	// 기록증에 적힌 거리(km)를 종목 구분으로. 5 · 10 · 하프 · 풀이 아니면 풀보다 길 때 울트라, 짧으면 기타
	public static CourseClass ofKm(double km) {
		for (CourseClass course : values()) {
			if (course != ULTRA && course.decimeters > 0 && Math.abs(km * 10_000 - course.decimeters) < 100) {
				return course;
			}
		}
		return km * 10_000 > FULL.decimeters ? ULTRA : OTHER;
	}

	// 풀 2시간 속도(약 170.6초/km)보다 빠르거나 같으면 true (판정 J7). 거리를 모르면 false
	boolean isTooFast(int netSeconds) {
		return decimeters > 0 && (long)netSeconds * FULL.decimeters <= 2L * 3600 * decimeters;
	}
}
