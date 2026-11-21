import { describe, expect, it } from 'vitest'

import {
  COURSE_DISTANCE_KM,
  COURSE_DRAFT_DEFAULTS,
  COURSE_FORM_ERRORS,
  courseTypeOptionLabel,
  createCourseDraftSchema,
  createRaceFormSchema,
  KIT_OPTION_ERRORS,
  RACE_FORM_DEFAULTS,
  RACE_FORM_ERRORS,
  raceFormSchema,
  saleOpenTooEarlyMessage,
  toCourseInputFromParsed,
  toKitOptionsRequest,
  toRaceSaveRequest,
} from './race-form'
import { SAMPLE_COURSES } from './race-form.fixtures'

// 크루 예매를 열지 않는 대회. 날짜·시각 칸은 'yyyy-MM-dd HH:mm'
const base = {
  ...RACE_FORM_DEFAULTS,
  title: '2026 용감한 쿠키RUN in 서울',
  location: '서울숲 일대',
  regionSido: '서울특별시',
  regionSigungu: '성동구',
  raceDate: '2026-10-29',
  raceTime: '08:00',
  individualOpenAt: '2026-10-23 10:00',
  applicationCloseAt: '2026-10-28 23:59',
  refundDeadline: '2026-10-28',
  courses: SAMPLE_COURSES,
}

// 크루 예매를 여는 대회. 크루 마감 10/22 23:59 → 일반 판매는 10/23 00:00부터
const crewBase = {
  ...base,
  crewOpen: true,
  crewSaleStartAt: '2026-10-19 10:00',
  crewSaleEndAt: '2026-10-22 23:59',
}

const issuesOf = (values: object, today?: Date) => {
  const result = createRaceFormSchema({ today }).safeParse(values)
  return result.success ? [] : result.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`)
}

describe('raceFormSchema 날짜 칸', () => {
  it('날짜와 시각을 모두 채우면 통과한다', () => {
    expect(raceFormSchema.safeParse(base).success).toBe(true)
  })

  it('날짜를 안 골랐거나 없는 날이면 날짜를 고르라고 한다', () => {
    expect(issuesOf({ ...base, raceDate: '' })).toEqual([
      `raceDate: ${RACE_FORM_ERRORS.dateRequired}`,
    ])
    expect(issuesOf({ ...base, raceDate: '2026-02-30' })).toEqual([
      `raceDate: ${RACE_FORM_ERRORS.dateRequired}`,
    ])
  })

  it('출발 시각이 비었거나 맞지 않으면 막는다', () => {
    for (const raceTime of ['', '8:00', '25:00', '08:60']) {
      expect(issuesOf({ ...base, raceTime })).toEqual([
        `raceTime: ${RACE_FORM_ERRORS.timeRequired}`,
      ])
    }
  })

  it('일반 판매 시작 · 접수 마감은 시각까지 있어야 한다', () => {
    for (const individualOpenAt of ['', '2026-10-23', '2026-10-23 ', '2026-10-23 24:00']) {
      expect(issuesOf({ ...base, individualOpenAt })).toEqual([
        `individualOpenAt: ${RACE_FORM_ERRORS.dateTimeRequired}`,
      ])
    }
    expect(issuesOf({ ...base, applicationCloseAt: '2026-10-28' })).toEqual([
      `applicationCloseAt: ${RACE_FORM_ERRORS.dateTimeRequired}`,
    ])
  })
})

describe('일정 순서 규칙 (서버 RaceRequestValidator · Race와 같다)', () => {
  it('일반 판매 시작 < 접수 마감 ≤ 대회 시작, 환불 기한 ≤ 대회일이면 통과한다', () => {
    expect(issuesOf(base)).toEqual([])
  })

  it('접수 마감이 일반 판매 시작과 같거나 빠르면 막는다', () => {
    const closeAfterOpen = `applicationCloseAt: ${RACE_FORM_ERRORS.closeAfterOpen}`
    expect(issuesOf({ ...base, applicationCloseAt: '2026-10-23 10:00' })).toEqual([closeAfterOpen])
    expect(issuesOf({ ...base, applicationCloseAt: '2026-10-22 23:59' })).toEqual([closeAfterOpen])
    expect(issuesOf({ ...base, applicationCloseAt: '2026-10-23 10:01' })).toEqual([])
  })

  it('접수 마감이 대회 시작보다 늦으면 막고, 같은 시각은 통과한다', () => {
    expect(issuesOf({ ...base, applicationCloseAt: '2026-10-29 08:01' })).toEqual([
      `applicationCloseAt: ${RACE_FORM_ERRORS.closeBeforeRace}`,
    ])
    expect(issuesOf({ ...base, applicationCloseAt: '2026-10-29 08:00' })).toEqual([])
  })

  it('환불 기한이 대회일보다 늦으면 막고, 같은 날은 통과한다', () => {
    expect(issuesOf({ ...base, refundDeadline: '2026-10-30' })).toEqual([
      `refundDeadline: ${RACE_FORM_ERRORS.refundBeforeRace}`,
    ])
    expect(issuesOf({ ...base, refundDeadline: '2026-10-29' })).toEqual([])
  })

  it('오늘을 주면 판매 시작이 오늘로부터 이틀 뒤 이전일 때 막는다', () => {
    const today = new Date(2026, 9, 21)
    expect(issuesOf({ ...base, individualOpenAt: '2026-10-22 10:00' }, today)).toEqual([
      `individualOpenAt: ${saleOpenTooEarlyMessage(new Date(2026, 9, 23))}`,
    ])
    expect(issuesOf({ ...base, individualOpenAt: '2026-10-23 00:00' }, today)).toEqual([])
  })
})

describe('크루 예매 일정 (서버 RaceRequestValidator.validateSchedule과 같다)', () => {
  it('크루 예매를 열면 시작 · 마감을 모두 받고, 순서가 맞으면 통과한다', () => {
    expect(issuesOf(crewBase)).toEqual([])
  })

  it('크루 예매를 열었는데 시작 · 마감이 비었으면 날짜와 시각을 입력하라고 한다', () => {
    expect(issuesOf({ ...crewBase, crewSaleStartAt: '', crewSaleEndAt: '' })).toEqual([
      `crewSaleStartAt: ${RACE_FORM_ERRORS.dateTimeRequired}`,
      `crewSaleEndAt: ${RACE_FORM_ERRORS.dateTimeRequired}`,
    ])
  })

  it('크루 예매 마감은 시작보다 늦어야 한다', () => {
    const message = `crewSaleEndAt: ${RACE_FORM_ERRORS.crewEndAfterStart}`
    expect(issuesOf({ ...crewBase, crewSaleEndAt: '2026-10-19 10:00' })).toEqual([message])
    expect(issuesOf({ ...crewBase, crewSaleEndAt: '2026-10-19 09:00' })).toEqual([message])
  })

  it('일반 판매 시작은 크루 예매 마감 다음 날 00:00 이후여야 한다 (마감이 00:00이면 그 시각부터)', () => {
    const message = `individualOpenAt: ${RACE_FORM_ERRORS.individualAfterCrew}`
    expect(issuesOf({ ...crewBase, individualOpenAt: '2026-10-22 23:59' })).toEqual([message])
    expect(issuesOf({ ...crewBase, individualOpenAt: '2026-10-23 00:00' })).toEqual([])
    const midnightEnd = { ...crewBase, crewSaleEndAt: '2026-10-23 00:00' }
    expect(issuesOf({ ...midnightEnd, individualOpenAt: '2026-10-23 00:00' })).toEqual([])
    expect(issuesOf({ ...midnightEnd, individualOpenAt: '2026-10-22 23:59' })).toEqual([message])
  })

  it('크루 예매를 열면 판매 시작일은 크루 예매 시작일이라 오늘로부터 이틀 뒤부터 받는다', () => {
    const today = new Date(2026, 9, 18)
    expect(issuesOf(crewBase, today)).toEqual([
      `crewSaleStartAt: ${saleOpenTooEarlyMessage(new Date(2026, 9, 20))}`,
    ])
    expect(issuesOf(crewBase, new Date(2026, 9, 17))).toEqual([])
  })

  it('크루 예매를 열지 않으면 크루 예매 칸은 비어 있어도 · 틀려도 보지 않는다', () => {
    expect(issuesOf({ ...base, crewSaleStartAt: '', crewSaleEndAt: '2026-10-01 10:00' })).toEqual(
      [],
    )
  })
})

describe('toRaceSaveRequest 시각', () => {
  it('고른 시각을 그대로 붙이고, 23:59는 초까지 59로 보내며, 환불 기한은 날짜만 보낸다', () => {
    const request = toRaceSaveRequest(base, [], [])
    expect(request.raceDate).toBe('2026-10-29T08:00:00')
    expect(request.individualOpenAt).toBe('2026-10-23T10:00:00')
    expect(request.applicationCloseAt).toBe('2026-10-28T23:59:59')
    expect(request.refundDeadline).toBe('2026-10-28')
  })

  it('크루 예매를 열지 않으면 크루 예매 기간을 빼고 크루 정원을 0으로 보낸다', () => {
    const request = toRaceSaveRequest(
      {
        ...base,
        crewSaleStartAt: '2026-10-19 10:00',
        crewSaleEndAt: '2026-10-22 23:59',
        courses: base.courses.map((course) => ({ ...course, crewQuota: 300 })),
      },
      [],
      [[], []],
    )
    expect(request.crewSaleStartAt).toBeUndefined()
    expect(request.crewSaleEndAt).toBeUndefined()
    expect(request.courses.map((c) => c.crewQuota)).toEqual([0, 0])
  })

  it('크루 예매를 열면 크루 예매 기간과 종목별 크루 · 일반 정원을 그대로 보낸다', () => {
    const request = toRaceSaveRequest(
      {
        ...crewBase,
        courses: [{ ...crewBase.courses[0], crewQuota: 600, individualQuota: 900 }],
      },
      [],
      [[]],
    )
    expect(request.crewSaleStartAt).toBe('2026-10-19T10:00:00')
    expect(request.crewSaleEndAt).toBe('2026-10-22T23:59:59')
    expect(request.courses[0]).toMatchObject({ crewQuota: 600, individualQuota: 900 })
  })
})

describe('종목 정원 칸 (서버 validateCourse와 같다)', () => {
  const course = (crewQuota: number, individualQuota: number) => ({
    ...base.courses[0],
    crewQuota,
    individualQuota,
  })

  it('크루 정원 + 일반 정원이 1명 이상이면 통과하고, 0이면 막는다', () => {
    expect(issuesOf({ ...crewBase, courses: [course(0, 1)] })).toEqual([])
    expect(issuesOf({ ...crewBase, courses: [course(1, 0)] })).toEqual([])
    expect(issuesOf({ ...crewBase, courses: [course(0, 0)] })).toEqual([
      `courses.0.individualQuota: ${COURSE_FORM_ERRORS.quotaSum}`,
    ])
  })

  it('크루 예매를 열지 않으면 크루 정원은 0으로 세므로 일반 정원이 0이면 막는다', () => {
    expect(issuesOf({ ...base, courses: [course(500, 0)] })).toEqual([
      `courses.0.individualQuota: ${COURSE_FORM_ERRORS.quotaSum}`,
    ])
  })

  it('정원이 숫자가 아니거나 음수 · 소수면 숫자 안내 문구를 보여 준다', () => {
    for (const bad of [Number.NaN, -1, 1.5]) {
      expect(issuesOf({ ...crewBase, courses: [course(bad, 10)] })).toEqual([
        `courses.0.crewQuota: ${COURSE_FORM_ERRORS.quotaNumber}`,
      ])
    }
  })
})

describe('종목 추가 창 규칙 (createCourseDraftSchema)', () => {
  const draft = {
    ...COURSE_DRAFT_DEFAULTS,
    name: '하프',
    courseType: 'HALF' as const,
    crewQuota: '600',
    individualQuota: '900',
    price: '55,000',
    timeLimitMinutes: '180',
    kitIncluded: 'included' as const,
    minAge: 19 as const,
  }
  const open = createCourseDraftSchema({ crewOpen: true })
  const closed = createCourseDraftSchema({ crewOpen: false })

  // 칸별 첫 오류 문구
  function errorsOf(schema: typeof open, input: Record<string, unknown>) {
    const result = schema.safeParse(input)
    if (result.success) throw new Error('오류가 나야 한다')
    const errors: Record<string, string> = {}
    for (const issue of result.error.issues) errors[String(issue.path[0])] ??= issue.message
    return errors
  }

  it('종목 구분이 거리를 정한다: 5K 5, 10K 10, 하프 21.1, 풀 42.2', () => {
    expect(COURSE_DISTANCE_KM).toEqual({ KM_5: 5, KM_10: 10, HALF: 21.1, FULL: 42.2 })
    expect(courseTypeOptionLabel('HALF')).toBe('하프 (21.1km)')
    expect(courseTypeOptionLabel('KM_5')).toBe('5K (5km)')
  })

  it('칸이 맞으면 종목 한 줄을 만들고, 거리는 종목 구분에서 정해 등록 요청에 넣는다', () => {
    const result = open.safeParse(draft)
    if (!result.success) throw new Error('종목이 만들어져야 한다')
    const course = toCourseInputFromParsed(result.data)
    expect(course).toMatchObject({
      name: '하프',
      courseType: 'HALF',
      distanceKm: 21.1,
      timeLimitMinutes: 180,
      price: 55000,
      crewQuota: 600,
      individualQuota: 900,
      kitIncluded: true,
      minAge: 19,
    })
    expect(course).not.toHaveProperty('maxAge')

    const request = toRaceSaveRequest(
      { ...base, crewOpen: true, courses: [...base.courses, course] },
      [],
      [[], [], []],
    )
    expect(request.courses.map((c) => c.name)).toEqual(['10K', '5K', '하프'])
    expect(request.courses.map((c) => c.distanceKm)).toEqual([10, 5, 21.1])
    expect(request.courses[2]).toMatchObject({ crewQuota: 600, individualQuota: 900, minAge: 19 })
  })

  it('키트를 없음으로 고르면 키트 포함 여부를 false로 만든다', () => {
    const result = open.safeParse({ ...draft, kitIncluded: 'none' })
    if (!result.success) throw new Error('종목이 만들어져야 한다')
    expect(toCourseInputFromParsed(result.data).kitIncluded).toBe(false)
  })

  it('참가비는 천 단위 쉼표를 지우고 숫자로 읽는다', () => {
    const result = open.safeParse({ ...draft, price: '1,234,000' })
    expect(result.success && result.data.price).toBe(1234000)
  })

  it('종목 구분은 울트라를 받지 않는다 (이미 저장된 울트라 대회는 서버가 그대로 둔다)', () => {
    expect(errorsOf(open, { ...draft, courseType: 'ULTRA' })).toEqual({
      courseType: COURSE_FORM_ERRORS.courseTypeRequired,
    })
  })

  it('필수 칸이 비었거나 숫자가 아니면 칸별 오류를 돌려준다', () => {
    expect(errorsOf(open, { ...COURSE_DRAFT_DEFAULTS, price: '5만원' })).toMatchObject({
      name: COURSE_FORM_ERRORS.nameRequired,
      courseType: COURSE_FORM_ERRORS.courseTypeRequired,
      crewQuota: COURSE_FORM_ERRORS.number,
      individualQuota: COURSE_FORM_ERRORS.number,
      price: COURSE_FORM_ERRORS.number,
      timeLimitMinutes: COURSE_FORM_ERRORS.number,
    })
  })

  it('나이는 14 · 19만 받는다', () => {
    expect(errorsOf(open, { ...draft, minAge: 20 })).toHaveProperty('minAge')
  })

  it('참가비 · 제한 시간이 0이면 막는다 (서버는 참가비 0원을 받지 않는다)', () => {
    expect(errorsOf(open, { ...draft, price: '0', timeLimitMinutes: '0' })).toEqual({
      price: COURSE_FORM_ERRORS.pricePositive,
      timeLimitMinutes: COURSE_FORM_ERRORS.timeLimitPositive,
    })
  })

  it('크루 예매를 열면 크루 정원 + 일반 정원이 1명 이상이어야 하고, 한쪽이 0이어도 된다', () => {
    expect(errorsOf(open, { ...draft, crewQuota: '0', individualQuota: '0' })).toEqual({
      individualQuota: COURSE_FORM_ERRORS.quotaSum,
    })
    expect(open.safeParse({ ...draft, crewQuota: '0', individualQuota: '5' }).success).toBe(true)
    expect(open.safeParse({ ...draft, crewQuota: '5', individualQuota: '0' }).success).toBe(true)
  })

  it('크루 예매를 열지 않으면 크루 정원은 받지 않고 0으로 만들며, 일반 정원이 1명 이상이어야 한다', () => {
    const result = closed.safeParse({ ...draft, crewQuota: '', individualQuota: '900' })
    if (!result.success) throw new Error('종목이 만들어져야 한다')
    expect(toCourseInputFromParsed(result.data).crewQuota).toBe(0)
    expect(errorsOf(closed, { ...draft, crewQuota: '', individualQuota: '0' })).toEqual({
      individualQuota: COURSE_FORM_ERRORS.quotaPositive,
    })
  })
})

describe('키트 옵션 칸 (서버 RaceRequestValidator.validateKitOptions와 같다)', () => {
  const kit = (name: string, value: string, stock = '') => ({ name, value, stock })
  const messages = (kitOptions: ReturnType<typeof kit>[]) => {
    const result = raceFormSchema.safeParse({ ...base, kitOptions })
    return result.success
      ? []
      : result.error.issues.filter((i) => i.path[0] === 'kitOptions').map((i) => i.message)
  }

  it('옵션이 없어도 대회는 등록할 수 있다', () => {
    expect(messages([])).toEqual([])
  })

  it('이름 · 값이 비었거나 준비 수량이 0 이상의 정수가 아니면 막는다', () => {
    expect(messages([kit(' ', 'M')])).toEqual([KIT_OPTION_ERRORS.nameRequired])
    expect(messages([kit('티셔츠', '')])).toEqual([KIT_OPTION_ERRORS.valueRequired])
    expect(messages([kit('티셔츠', 'M', '-1')])).toEqual([KIT_OPTION_ERRORS.stockNumber])
    expect(messages([kit('티셔츠', 'M', '1.5')])).toEqual([KIT_OPTION_ERRORS.stockNumber])
    expect(messages([kit('티셔츠', 'M', '0'), kit('티셔츠', 'L', '')])).toEqual([])
  })

  it('이름은 50자, 값은 50자까지 받는다', () => {
    expect(messages([kit('가'.repeat(51), 'M')])).toEqual([KIT_OPTION_ERRORS.nameMax])
    expect(messages([kit('티셔츠', '가'.repeat(51))])).toEqual([KIT_OPTION_ERRORS.valueMax])
  })

  it('이름 · 값이 대소문자와 앞뒤 공백만 다른 옵션은 같은 옵션이라 막는다', () => {
    expect(messages([kit('T-shirt', 'M'), kit('T-shirt', ' m ')])).toEqual([
      KIT_OPTION_ERRORS.duplicate,
    ])
  })

  it('같은 옵션 이름은 대소문자까지 똑같이 써야 한다', () => {
    expect(messages([kit('T-shirt', 'M'), kit('t-shirt', 'L')])).toEqual([
      KIT_OPTION_ERRORS.nameCase,
    ])
  })

  it('요청은 앞뒤 공백을 지우고, 준비 수량을 비우면 null(맞춤 제작)로 보낸다', () => {
    expect(toKitOptionsRequest([kit(' 티셔츠 ', ' M ', '100'), kit('양말', '230-250')])).toEqual({
      kitOptions: [
        { name: '티셔츠', value: 'M', stock: 100 },
        { name: '양말', value: '230-250', stock: null },
      ],
    })
  })

  it('대회 등록 요청에는 키트 옵션을 넣지 않는다 (키트 옵션은 따로 등록한다)', () => {
    const request = toRaceSaveRequest(base, [], [])
    expect(request).not.toHaveProperty('kitOptions')
    for (const course of request.courses) expect(course).not.toHaveProperty('tshirtSizeOption')
  })
})

describe('대회 소개 칸', () => {
  it('2,000자를 넘기면 막고, 비어 있으면 요청에 보내지 않는다', () => {
    const tooLong = createRaceFormSchema().safeParse({ ...base, description: 'a'.repeat(2001) })
    expect(tooLong.success).toBe(false)
    expect(toRaceSaveRequest(base, [], []).description).toBeUndefined()
  })

  it('앞뒤 공백을 지워 보낸다', () => {
    expect(toRaceSaveRequest({ ...base, description: '  가을 러닝  ' }, [], []).description).toBe(
      '가을 러닝',
    )
  })
})

describe('상세 주소 칸', () => {
  it('도로명 주소 뒤에 상세 주소를 붙여 장소로 보낸다', () => {
    const request = toRaceSaveRequest(
      { ...base, location: '서울특별시 성동구 뚝섬로 273', locationDetail: ' 서울숲 1번 출구 ' },
      [],
      [],
    )
    expect(request.location).toBe('서울특별시 성동구 뚝섬로 273 서울숲 1번 출구')
  })

  it('상세 주소가 비어 있으면 주소만 보낸다', () => {
    expect(toRaceSaveRequest(base, [], []).location).toBe('서울숲 일대')
  })

  it('상세 주소가 100자를 넘으면 막는다', () => {
    const result = createRaceFormSchema().safeParse({ ...base, locationDetail: 'a'.repeat(101) })
    expect(result.success).toBe(false)
  })
})

describe('종목별 최소 나이 (서버는 종목마다 14 또는 19만 받는다)', () => {
  it('종목마다 고른 최소 나이를 그대로 보내고 최대 나이는 보내지 않는다', () => {
    const request = toRaceSaveRequest(
      {
        ...base,
        courses: [
          { ...base.courses[0], minAge: 19 },
          { ...base.courses[1], minAge: 14 },
        ],
      },
      [],
      [[], []],
    )
    expect(request.courses.map((c) => c.minAge)).toEqual([19, 14])
    for (const course of request.courses) {
      expect(JSON.parse(JSON.stringify(course))).not.toHaveProperty('maxAge')
    }
  })

  it('기본은 모든 종목 만 14세이고, 14 · 19 말고는 검사에서 막는다', () => {
    expect(base.courses.map((c) => c.minAge)).toEqual([14, 14])
    expect(RACE_FORM_DEFAULTS).not.toHaveProperty('minAge')
    expect(issuesOf({ ...base, courses: [{ ...base.courses[0], minAge: 20 }] })).toHaveLength(1)
  })
})

describe('새 대회 등록의 기본값', () => {
  it('종목 · 키트 옵션은 빈 채로 시작한다 (판매자가 직접 추가한다)', () => {
    expect(RACE_FORM_DEFAULTS.courses).toEqual([])
    expect(RACE_FORM_DEFAULTS.kitOptions).toEqual([])
  })

  it('종목이 비어 있으면 종목을 추가하라고 하고, 키트 옵션이 비어 있는 것은 막지 않는다', () => {
    const issues = issuesOf({ ...base, courses: [] })
    expect(issues).toEqual([`courses: ${RACE_FORM_ERRORS.coursesRequired}`])
    expect(issuesOf({ ...base, kitOptions: [] })).toEqual([])
  })
})

describe('크루 최대 인원 (서버: 비우면 상한 없음, 값이 있으면 최소 인원 4 이상)', () => {
  const issues = (patch: object) => issuesOf({ ...crewBase, ...patch })
  const message = `crewMaxCount: ${RACE_FORM_ERRORS.crewMaxCount}`

  it('기본은 비어 있고, 비워도 통과한다', () => {
    expect(RACE_FORM_DEFAULTS.crewMaxCount).toBe('')
    expect(issues({ crewMaxCount: '' })).toEqual([])
  })

  it('최소 인원(4) 이상의 정수면 통과하고, 그보다 작거나 숫자가 아니면 막는다', () => {
    expect(issues({ crewMaxCount: '4' })).toEqual([])
    expect(issues({ crewMaxCount: '20' })).toEqual([])
    for (const crewMaxCount of ['3', '0', '4.5', 'abc', '-5']) {
      expect(issues({ crewMaxCount })).toEqual([message])
    }
  })

  it('크루 예매를 열지 않으면 값이 틀려도 보지 않는다', () => {
    expect(issuesOf({ ...base, crewMaxCount: '3' })).toEqual([])
  })

  it('크루 예매를 열면 요청에 담아 보내고, 비우면 보내지 않으며, 열지 않으면 값이 있어도 보내지 않는다', () => {
    expect(toRaceSaveRequest({ ...crewBase, crewMaxCount: '20' }, [], []).crewMaxCount).toBe(20)
    expect(
      toRaceSaveRequest({ ...crewBase, crewMaxCount: '' }, [], []).crewMaxCount,
    ).toBeUndefined()
    expect(toRaceSaveRequest({ ...base, crewMaxCount: '20' }, [], []).crewMaxCount).toBeUndefined()
  })

  it('크루 최소 인원은 서버 최솟값 4로 보낸다', () => {
    expect(toRaceSaveRequest(base, [], []).crewMinCount).toBe(4)
  })
})
