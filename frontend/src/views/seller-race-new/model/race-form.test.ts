import { describe, expect, it } from 'vitest'

import {
  COURSE_DRAFT_DEFAULTS,
  COURSE_FORM_ERRORS,
  createRaceFormSchema,
  RACE_FORM_DEFAULTS,
  RACE_FORM_ERRORS,
  raceFormSchema,
  saleOpenTooEarlyMessage,
  toCourseInput,
  toRaceSaveRequest,
} from './race-form'

const base = {
  ...RACE_FORM_DEFAULTS,
  title: '2026 용감한 쿠키RUN in 서울',
  location: '서울숲 일대',
  regionSido: '서울특별시',
  regionSigungu: '성동구',
  raceDate: '2026-10-29',
  individualOpenAt: '2026-10-23',
  applicationCloseAt: '2026-10-28',
  refundDeadline: '2026-10-28',
}

describe('raceFormSchema 날짜 칸', () => {
  const messagesAt = (patch: Partial<typeof base>, field: keyof typeof base) => {
    const result = raceFormSchema.safeParse({ ...base, ...patch })
    return result.success
      ? []
      : result.error.issues.filter((i) => i.path[0] === field).map((i) => i.message)
  }

  it('날짜만 고르면 통과한다', () => {
    expect(raceFormSchema.safeParse(base).success).toBe(true)
  })

  it('날짜를 안 골랐거나 없는 날이면 날짜를 고르라고 한다', () => {
    expect(messagesAt({ raceDate: '' }, 'raceDate')).toEqual([RACE_FORM_ERRORS.dateRequired])
    expect(messagesAt({ raceDate: '2026-02-30' }, 'raceDate')).toEqual([
      RACE_FORM_ERRORS.dateRequired,
    ])
  })
})

describe('일정 순서 규칙 (서버 RaceRequestValidator · Race와 같다)', () => {
  const issues = (patch: Partial<typeof base>, today?: Date) => {
    const result = createRaceFormSchema({ today }).safeParse({ ...base, ...patch })
    return result.success
      ? []
      : result.error.issues.map((i) => `${String(i.path[0])}: ${i.message}`)
  }

  it('접수 시작 ≤ 접수 마감 < 대회일, 환불 기한 ≤ 대회일이면 통과한다', () => {
    expect(issues({})).toEqual([])
  })

  it('접수 시작과 마감이 같은 날이면 하루짜리 접수로 통과한다', () => {
    expect(issues({ applicationCloseAt: '2026-10-23' })).toEqual([])
  })

  it('접수 마감이 접수 시작보다 빠르면 막는다', () => {
    expect(issues({ applicationCloseAt: '2026-10-22' })).toEqual([
      `applicationCloseAt: ${RACE_FORM_ERRORS.closeAfterOpen}`,
    ])
  })

  it('접수 마감은 대회 전날까지만 받는다 (마감 23:59:59가 대회 00:00보다 늦어지므로)', () => {
    expect(issues({ applicationCloseAt: '2026-10-29' })).toEqual([
      `applicationCloseAt: ${RACE_FORM_ERRORS.closeBeforeRace}`,
    ])
    expect(issues({ applicationCloseAt: '2026-10-30' })).toEqual([
      `applicationCloseAt: ${RACE_FORM_ERRORS.closeBeforeRace}`,
    ])
  })

  it('환불 기한이 대회일보다 늦으면 막고, 같은 날은 통과한다', () => {
    expect(issues({ refundDeadline: '2026-10-30' })).toEqual([
      `refundDeadline: ${RACE_FORM_ERRORS.refundBeforeRace}`,
    ])
    expect(issues({ refundDeadline: '2026-10-29' })).toEqual([])
  })

  it('오늘을 주면 접수 시작이 오늘로부터 이틀 뒤 이전일 때 막는다', () => {
    const today = new Date(2026, 9, 21)
    expect(issues({ individualOpenAt: '2026-10-22' }, today)).toEqual([
      `individualOpenAt: ${saleOpenTooEarlyMessage(new Date(2026, 9, 23))}`,
    ])
    expect(issues({ individualOpenAt: '2026-10-23' }, today)).toEqual([])
  })
})

describe('toRaceSaveRequest 기본 시각', () => {
  it('대회·접수 시작은 00:00, 접수 마감은 23:59:59를 붙이고 환불 기한은 날짜만 보낸다', () => {
    const request = toRaceSaveRequest(base, [])
    expect(request.raceDate).toBe('2026-10-29T00:00:00')
    expect(request.individualOpenAt).toBe('2026-10-23T00:00:00')
    expect(request.applicationCloseAt).toBe('2026-10-28T23:59:59')
    expect(request.refundDeadline).toBe('2026-10-28')
  })
})

describe('toCourseInput 종목 추가 창', () => {
  const draft = {
    ...COURSE_DRAFT_DEFAULTS,
    name: '하프',
    courseType: 'HALF' as const,
    distanceKm: '21.1',
    timeLimitMinutes: '180',
    price: '55000',
    individualQuota: '500',
    minAge: '18',
  }

  it('칸이 맞으면 종목 한 줄을 만들고, 등록 요청의 종목 목록에 그대로 들어간다', () => {
    const result = toCourseInput(draft)
    if (!('course' in result)) throw new Error('종목이 만들어져야 한다')
    expect(result.course).toMatchObject({
      name: '하프',
      courseType: 'HALF',
      distanceKm: 21.1,
      timeLimitMinutes: 180,
      price: 55000,
      individualQuota: 500,
      minAge: 18,
      maxAge: undefined,
      crewQuota: 0,
    })

    const request = toRaceSaveRequest({ ...base, courses: [...base.courses, result.course] }, [
      [],
      [],
      [],
    ])
    expect(request.courses.map((c) => c.name)).toEqual(['10K', '5K', '하프'])
  })

  it('빈 칸 · 숫자가 아닌 값은 칸별 오류로 돌려준다', () => {
    const result = toCourseInput({ ...COURSE_DRAFT_DEFAULTS, price: '5만원' })
    if (!('errors' in result)) throw new Error('오류가 나야 한다')
    expect(result.errors).toMatchObject({
      name: COURSE_FORM_ERRORS.nameRequired,
      courseType: COURSE_FORM_ERRORS.courseTypeRequired,
      price: COURSE_FORM_ERRORS.number,
    })
  })

  it('최대 나이가 최소 나이보다 작으면 막는다', () => {
    const result = toCourseInput({ ...draft, minAge: '20', maxAge: '19' })
    expect(result).toEqual({ errors: { maxAge: COURSE_FORM_ERRORS.maxAgeOrder } })
  })

  it('참가비 · 정원 · 제한 시간이 0이면 막는다 (서버는 참가비 0원을 받지 않는다)', () => {
    const result = toCourseInput({
      ...draft,
      price: '0',
      individualQuota: '0',
      timeLimitMinutes: '0',
    })
    expect(result).toEqual({
      errors: {
        price: COURSE_FORM_ERRORS.pricePositive,
        individualQuota: COURSE_FORM_ERRORS.quotaPositive,
        timeLimitMinutes: COURSE_FORM_ERRORS.timeLimitPositive,
      },
    })
  })
})
