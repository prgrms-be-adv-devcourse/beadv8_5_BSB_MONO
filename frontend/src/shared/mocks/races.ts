import type {
  RaceDetailCourseDto,
  RaceDetailDto,
  RacePageDto,
  RaceSummaryDto,
  SearchParams,
} from '@/shared/api/generated/product/product.schemas'

// 가짜 응답 모드의 대회 목록. 명세 예시는 대회가 1개뿐이라 홈 · 대회 찾기가 비어 보여, 직접 만든 대회 8개를 쓴다.
// 날짜는 "지금"에서 며칠 뒤로 잡아, 언제 열어도 접수 중 · 마감 임박 · 마감 · 오픈 예정이 골고루 보인다.

type Detail = RaceDetailDto
type CourseType = NonNullable<RaceDetailCourseDto['courseType']>

const DAY_MS = 24 * 60 * 60 * 1000
const KST_OFFSET_MS = 9 * 60 * 60 * 1000

/** 오늘(한국 시간)에서 days일 뒤 hour시의, 시간대 없는 백엔드 모양 시각 (2026-10-12T10:10:00) */
function kstAt(days: number, hour: number, now: number): string {
  const date = new Date(now + KST_OFFSET_MS + days * DAY_MS)
  date.setUTCHours(hour, 0, 0, 0)
  return date.toISOString().slice(0, 19)
}

const DISTANCE: Record<CourseType, number> = {
  KM_5: 5,
  KM_10: 10,
  HALF: 21.0975,
  FULL: 42.195,
  ULTRA: 50,
}
const TIME_LIMIT_MINUTES: Record<CourseType, number> = {
  KM_5: 60,
  KM_10: 90,
  HALF: 180,
  FULL: 360,
  ULTRA: 600,
}

type CourseSeed = {
  id: number
  name: string
  type: CourseType
  price: number
  quota: number
  /** 남은 자리. 0이면 정원 마감 */
  remaining: number
  trail?: boolean
}

type RaceSeed = {
  id: number
  title: string
  description: string
  location: string
  sido: string
  sigungu: string
  /** 대회일 · 접수 마감 · 개인 예매 오픈이 오늘에서 며칠 뒤인지 (음수는 지난 날) */
  raceInDays: number
  closeInDays: number
  openInDays: number
  closeHour?: number
  /** 다른 화면(내 참가권 · 판매자 화면)의 시안과 테스트가 명세 예시 날짜를 쓰는 대회는 날짜를 고정한다 */
  fixed?: { raceDate: string; individualOpenAt: string; applicationCloseAt: string }
  /** 등록한 지 며칠 됐는지 (최신 등록순 정렬용) */
  registeredDaysAgo: number
  image: string
  courses: CourseSeed[]
}

const RACE_SEEDS: RaceSeed[] = [
  {
    id: 1,
    title: '2026 용감한 쿠키RUN in 서울',
    description: '서울숲에서 달리는 가을 러닝 대회',
    location: '서울숲 일대',
    sido: '서울특별시',
    sigungu: '성동구',
    raceInDays: 12,
    closeInDays: 10,
    openInDays: -2,
    fixed: {
      raceDate: '2026-11-01T08:00:00',
      individualOpenAt: '2026-10-20T09:00:00',
      applicationCloseAt: '2026-10-31T18:00:00',
    },
    registeredDaysAgo: 6,
    image: '/mock/races/cookierun.webp',
    courses: [
      { id: 1, name: '10K', type: 'KM_10', price: 45000, quota: 600, remaining: 580 },
      { id: 2, name: '5K', type: 'KM_5', price: 35000, quota: 500, remaining: 410 },
    ],
  },
  {
    id: 2,
    title: '긍정 하프 마라톤 YOUTH 10K',
    description: '올림픽공원을 도는 10K 대회. 접수가 곧 끝나요',
    location: '올림픽공원 평화의 광장',
    sido: '서울특별시',
    sigungu: '송파구',
    raceInDays: 9,
    closeInDays: 2,
    openInDays: -8,
    registeredDaysAgo: 12,
    image: '/mock/races/youth10k.webp',
    courses: [{ id: 11, name: '10K', type: 'KM_10', price: 40000, quota: 1000, remaining: 37 }],
  },
  {
    id: 3,
    title: '2026 서울마라톤',
    description: '광화문에서 출발해 잠실로 들어오는 국내 최고 역사의 마라톤',
    location: '광화문광장',
    sido: '서울특별시',
    sigungu: '종로구',
    raceInDays: 25,
    closeInDays: 18,
    openInDays: -3,
    registeredDaysAgo: 4,
    image: '/mock/races/seoul-international-marathon.webp',
    courses: [
      { id: 21, name: '10K', type: 'KM_10', price: 50000, quota: 2000, remaining: 1480 },
      { id: 22, name: '풀', type: 'FULL', price: 90000, quota: 1500, remaining: 902 },
    ],
  },
  {
    id: 4,
    title: 'JTBC 서울 마라톤',
    description: '잠실종합운동장에서 출발해 서울 도심을 달리는 풀코스 대회',
    location: '잠실종합운동장',
    sido: '서울특별시',
    sigungu: '송파구',
    raceInDays: 30,
    closeInDays: 1,
    closeHour: 23,
    openInDays: -10,
    registeredDaysAgo: 15,
    image: '/mock/races/jtbc.webp',
    courses: [
      { id: 31, name: '풀', type: 'FULL', price: 90000, quota: 3000, remaining: 0 },
      { id: 32, name: '하프', type: 'HALF', price: 70000, quota: 3000, remaining: 214 },
      { id: 33, name: '10K', type: 'KM_10', price: 55000, quota: 2000, remaining: 96 },
    ],
  },
  {
    id: 5,
    title: '부산바다마라톤',
    description: '광안대교를 바라보며 달리는 부산의 해변 마라톤',
    location: '광안리 해수욕장',
    sido: '부산광역시',
    sigungu: '수영구',
    raceInDays: 18,
    closeInDays: 11,
    openInDays: -4,
    registeredDaysAgo: 3,
    image: '/mock/races/busan-beach-run.webp',
    courses: [
      { id: 41, name: '5K', type: 'KM_5', price: 30000, quota: 800, remaining: 640 },
      { id: 42, name: '10K', type: 'KM_10', price: 38000, quota: 800, remaining: 505 },
    ],
  },
  {
    id: 6,
    title: '제주감귤국제마라톤',
    description: '감귤 밭 사이를 달리는 제주 대표 마라톤',
    location: '조천운동장 일대',
    sido: '제주특별자치도',
    sigungu: '제주시',
    raceInDays: 40,
    closeInDays: 30,
    openInDays: -1,
    registeredDaysAgo: 1,
    image: '/mock/races/jeju-trail-run.webp',
    courses: [
      { id: 51, name: '10K', type: 'KM_10', price: 48000, quota: 400, remaining: 372 },
      { id: 52, name: '하프', type: 'HALF', price: 62000, quota: 300, remaining: 261 },
    ],
  },
  {
    id: 7,
    title: '대구마라톤',
    description: '대구스타디움을 출발해 도심을 달리는 마라톤. 곧 접수를 시작해요',
    location: '대구스타디움',
    sido: '대구광역시',
    sigungu: '수성구',
    raceInDays: 28,
    closeInDays: 20,
    openInDays: 5,
    registeredDaysAgo: 0,
    image: '/mock/races/daegu-marathon.webp',
    courses: [
      { id: 61, name: '풀', type: 'FULL', price: 90000, quota: 700, remaining: 700 },
      { id: 62, name: '10K', type: 'KM_10', price: 38000, quota: 700, remaining: 700 },
    ],
  },
  {
    id: 10,
    title: '춘천마라톤',
    description: '의암호를 따라 달리는 가을 마라톤. 접수가 끝났어요',
    location: '공지천 일대',
    sido: '강원특별자치도',
    sigungu: '춘천시',
    raceInDays: 3,
    closeInDays: -1,
    openInDays: -20,
    registeredDaysAgo: 25,
    image: '/mock/races/chuncheon-lake-run.webp',
    courses: [
      { id: 101, name: '풀', type: 'FULL', price: 80000, quota: 500, remaining: 0 },
      { id: 102, name: '10K', type: 'KM_10', price: 32000, quota: 500, remaining: 0 },
    ],
  },
]

type MockRace = {
  detail: Detail
  summary: RaceSummaryDto
  image: string
  registeredAt: number
}

function build(seed: RaceSeed, now: number): MockRace {
  const base = {
    raceId: seed.id,
    title: seed.title,
    location: seed.location,
    regionSido: seed.sido,
    regionSigungu: seed.sigungu,
    raceDate: kstAt(seed.raceInDays, 8, now),
    saleStatus: seed.openInDays > 0 && !seed.fixed ? ('SCHEDULED' as const) : ('ON_SALE' as const),
    saleOpenDate: kstAt(seed.openInDays, 0, now).slice(0, 10),
    crewSaleStartAt: null,
    crewSaleEndAt: null,
    individualOpenAt: kstAt(seed.openInDays, 9, now),
    applicationCloseAt: kstAt(seed.closeInDays, seed.closeHour ?? 18, now),
    ...seed.fixed,
  }
  const courses = seed.courses.map((course) => ({
    raceCourseId: course.id,
    name: course.name,
    courseType: course.type,
    distanceKm: DISTANCE[course.type],
    price: course.price,
    status: course.remaining > 0 ? ('OPEN' as const) : ('SOLD_OUT' as const),
    individualRemaining: course.remaining,
  }))
  const detail: Detail = {
    ...base,
    description: seed.description,
    refundDeadline: base.applicationCloseAt.slice(0, 10),
    refundPolicy: '환불 기한까지 취소하면 전액 환불됩니다.',
    crewMinCount: 5,
    crewMaxCount: null,
    scheduleChangedAt: null,
    seller: {
      sellerId: 7000 + seed.id,
      sellerType: 'BUSINESS',
      businessName: `(주)${seed.title.replace(/^\d{4} /, '').split(' ')[0]}`,
      businessNumber: '123-45-67890',
      representativeName: '김대표',
    },
    courses: seed.courses.map((course) => ({
      raceCourseId: course.id,
      name: course.name,
      courseType: course.type,
      surface: course.trail ? 'TRAIL' : 'ROAD',
      distanceKm: DISTANCE[course.type],
      timeLimitMinutes: TIME_LIMIT_MINUTES[course.type],
      minAge: 14,
      maxAge: null,
      price: course.price,
      kitIncluded: true,
      kitPrice: 0,
      // TODO(BUK-270): 공개 종목 조회에 "기록증 필요 여부" 칸이 생기면 명세 타입으로 옮긴다. 기록증 제출 시안용으로 1번 종목만 켠다
      ...{ recordCertRequired: course.id === 1 },
      tshirtSizeOption: ['S', 'M', 'L', 'XL'],
      status: course.remaining > 0 ? 'OPEN' : 'SOLD_OUT',
      crewQuota: 0,
      individualQuota: course.quota,
      crewRemaining: 0,
      individualRemaining: course.remaining,
    })),
  }
  const summary: RaceSummaryDto = {
    ...base,
    imageUrl: seed.image,
    minPrice: Math.min(...seed.courses.map((course) => course.price)),
    courses,
  }
  return { detail, summary, image: seed.image, registeredAt: now - seed.registeredDaysAgo * DAY_MS }
}

/** 지금 기준으로 날짜를 맞춘 가짜 대회 목록 */
export function getMockRaces(now: number = Date.now()): MockRace[] {
  return RACE_SEEDS.map((seed) => build(seed, now))
}

export function findMockRace(raceId: number, now: number = Date.now()): MockRace | undefined {
  return getMockRaces(now).find((race) => race.detail.raceId === raceId)
}

/** 대회 ID로 그 대회의 대표 이미지 (없으면 undefined) */
export function mockImageOfRace(raceId: number): string | undefined {
  return RACE_SEEDS.find((race) => race.id === raceId)?.image
}

/** 종목 ID로 그 종목이 속한 대회의 이미지 (없으면 undefined) */
export function mockImageOfCourse(raceCourseId: number): string | undefined {
  const seed = RACE_SEEDS.find((race) => race.courses.some((course) => course.id === raceCourseId))
  return seed?.image
}

/** 접수 마감이 지났거나 판매가 끝난 대회 */
function isClosed(race: RaceSummaryDto, now: number): boolean {
  const closeAt = race.applicationCloseAt
  return !!closeAt && new Date(`${closeAt}+09:00`).getTime() <= now
}

function matchesKeyword(race: RaceSummaryDto, description: string, keyword: string): boolean {
  const text = [
    race.title,
    race.location,
    race.regionSido,
    race.regionSigungu,
    description,
    ...(race.courses ?? []).map((course) => course.name),
  ]
    .join(' ')
    .toLowerCase()
  return text.includes(keyword.toLowerCase())
}

const byText = (a?: string, b?: string) => (a ?? '').localeCompare(b ?? '')

/**
 * 대회 찾기 요청(keyword · sort · page · size)대로 걸러 · 정렬 · 잘라서 돌려준다.
 * 정렬: DEADLINE은 접수 마감이 가까운 순(마감된 대회는 맨 뒤), RACE_DATE는 대회일 빠른 순,
 * LATEST는 최근 등록 순, PRICE는 최저 참가비 낮은 순. 보내지 않으면 검색어가 있을 때 RELEVANCE, 없을 때 DEADLINE.
 * RELEVANCE는 이름에 검색어가 들어간 대회를 앞에 둔다.
 */
export function searchMockRaces(params: SearchParams, now: number = Date.now()): RacePageDto {
  const keyword = params.keyword?.trim() ?? ''
  const sort = params.sort ?? (keyword ? 'RELEVANCE' : 'DEADLINE')
  const page = Math.max(0, params.page ?? 0)
  const size = Math.min(100, Math.max(1, params.size ?? 20))

  const found = getMockRaces(now).filter(
    (race) => !keyword || matchesKeyword(race.summary, race.detail.description ?? '', keyword),
  )
  const compare: Record<NonNullable<SearchParams['sort']>, (a: MockRace, b: MockRace) => number> = {
    DEADLINE: (a, b) =>
      Number(isClosed(a.summary, now)) - Number(isClosed(b.summary, now)) ||
      byText(a.summary.applicationCloseAt, b.summary.applicationCloseAt),
    RACE_DATE: (a, b) => byText(a.summary.raceDate, b.summary.raceDate),
    LATEST: (a, b) => b.registeredAt - a.registeredAt,
    PRICE: (a, b) => (a.summary.minPrice ?? 0) - (b.summary.minPrice ?? 0),
    RELEVANCE: (a, b) => {
      const inTitle = (race: MockRace) =>
        Number(!(race.summary.title ?? '').toLowerCase().includes(keyword.toLowerCase()))
      return inTitle(a) - inTitle(b) || compare.DEADLINE(a, b)
    },
  }
  const sorted = [...found].sort(compare[sort])

  return {
    races: sorted.slice(page * size, (page + 1) * size).map((race) => race.summary),
    page,
    size,
    totalElements: sorted.length,
    totalPages: Math.ceil(sorted.length / size),
  }
}
