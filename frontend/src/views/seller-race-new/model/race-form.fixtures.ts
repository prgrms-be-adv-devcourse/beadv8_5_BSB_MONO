import type { RaceCourseInput } from './race-form'

// 테스트용 종목 두 줄. 화면은 빈 종목 표로 시작하므로 기본값이 아니라 여기서 만든다
export const TEN_K: RaceCourseInput = {
  name: '10K',
  courseType: 'KM_10',
  surface: 'ROAD',
  distanceKm: 10,
  timeLimitMinutes: 90,
  price: 45000,
  kitIncluded: true,
  kitPrice: 0,
  minAge: 14,
  crewQuota: 0,
  individualQuota: 2000,
}

export const FIVE_K: RaceCourseInput = {
  ...TEN_K,
  name: '5K',
  courseType: 'KM_5',
  distanceKm: 5,
  timeLimitMinutes: 60,
  price: 35000,
  individualQuota: 1000,
}

export const SAMPLE_COURSES = [TEN_K, FIVE_K]
