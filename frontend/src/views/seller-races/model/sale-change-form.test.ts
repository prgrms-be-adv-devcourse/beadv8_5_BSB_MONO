import { describe, expect, it } from 'vitest'

import type { RaceForEdit } from '@/entities/race'
import type { productModel } from '@/shared/api'

import {
  SALE_CHANGE_ERRORS,
  saleChangeSchema,
  scheduleLocks,
  toSaleChangeValues,
  toUpdateRequest,
  type SaleChangeValues,
} from './sale-change-form'

// 지금: 2026-11-21 12:00 (한국 시간)
const NOW = new Date('2026-11-21T12:00:00+09:00')

type Schedule = Pick<
  productModel.RaceSaveRequestDto,
  'crewSaleStartAt' | 'crewSaleEndAt' | 'individualOpenAt' | 'applicationCloseAt'
>

// 크루 예매가 있는 판매 예정 대회: 모든 일정이 아직 오지 않았다
const CREW_SCHEDULE: Schedule = {
  crewSaleStartAt: '2026-11-30T10:00:00',
  crewSaleEndAt: '2026-12-04T23:59:59',
  individualOpenAt: '2026-12-05T00:00:00',
  applicationCloseAt: '2026-12-20T18:00:00',
}
const PLAIN_SCHEDULE: Schedule = {
  individualOpenAt: '2026-11-30T10:00:00',
  applicationCloseAt: '2026-12-20T18:00:00',
}

function makeEdit(
  saleStatus: 'SCHEDULED' | 'ON_SALE',
  schedule: Schedule,
  crewQuota = 0,
): RaceForEdit {
  const request = {
    title: '대회',
    location: '서울숲',
    regionSido: '서울특별시',
    regionSigungu: '성동구',
    raceDate: '2026-12-21T08:00:00',
    refundDeadline: '2026-12-10',
    crewMinCount: 5,
    images: [],
    ...schedule,
    courses: [
      {
        courseId: 1,
        name: '10K',
        price: 45000,
        crewQuota,
        individualQuota: 100,
        images: [],
      },
    ],
  } as unknown as productModel.RaceSaveRequestDto
  return { race: { saleStatus }, request, files: { race: [], courses: {} } } as RaceForEdit
}

function validate(edit: RaceForEdit, patch: Partial<SaleChangeValues> = {}, now = NOW) {
  const values = { ...toSaleChangeValues(edit), ...patch }
  const result = saleChangeSchema(edit, now).safeParse(values)
  const issues = result.success ? [] : result.error.issues
  return issues.map((i) => ({ path: i.path.join('.'), message: i.message }))
}

describe('판매 일정 입력 칸 채우기', () => {
  it('크루 예매 기간이 있으면 열기로 열고 저장된 시각으로 채운다', () => {
    const values = toSaleChangeValues(makeEdit('SCHEDULED', CREW_SCHEDULE))
    expect(values).toMatchObject({
      crewOpen: true,
      crewSaleStartAt: '2026-11-30 10:00',
      crewSaleEndAt: '2026-12-04 23:59',
      individualOpenAt: '2026-12-05 00:00',
      applicationCloseAt: '2026-12-20 18:00',
    })
  })

  it('크루 예매 기간이 없으면 열지 않기로 열고 크루 칸은 비운다', () => {
    const values = toSaleChangeValues(makeEdit('SCHEDULED', PLAIN_SCHEDULE))
    expect(values).toMatchObject({ crewOpen: false, crewSaleStartAt: '', crewSaleEndAt: '' })
  })
})

describe('일정 수정 제한 (서버 Race.update · checkScheduleChange와 같다)', () => {
  it('바꾸지 않은 일정은 이미 지났어도 막지 않는다', () => {
    const past: Schedule = {
      crewSaleStartAt: '2026-11-18T10:00:00',
      crewSaleEndAt: '2026-11-25T23:59:59',
      individualOpenAt: '2026-11-26T00:00:00',
      applicationCloseAt: '2026-12-20T18:00:00',
    }
    expect(validate(makeEdit('ON_SALE', past))).toEqual([])
  })

  it('판매가 시작된 대회는 크루 예매 시작을 바꿀 수 없다', () => {
    const edit = makeEdit('ON_SALE', {
      ...CREW_SCHEDULE,
      crewSaleStartAt: '2026-11-30T10:00:00',
    })
    expect(validate(edit, { crewSaleStartAt: '2026-11-29 10:00' })).toContainEqual({
      path: 'crewSaleStartAt',
      message: SALE_CHANGE_ERRORS.scheduleLocked,
    })
  })

  it('판매가 시작된 크루 예매 없는 대회는 일반 판매 시작을 바꿀 수 없다', () => {
    const edit = makeEdit('ON_SALE', PLAIN_SCHEDULE)
    expect(validate(edit, { individualOpenAt: '2026-12-01 10:00' })).toContainEqual({
      path: 'individualOpenAt',
      message: SALE_CHANGE_ERRORS.scheduleLocked,
    })
  })

  it('판매가 시작된 대회도 아직 오지 않은 접수 마감은 늦출 수 있다', () => {
    const edit = makeEdit('ON_SALE', PLAIN_SCHEDULE)
    expect(validate(edit, { applicationCloseAt: '2026-12-21 07:00' })).toEqual([])
  })

  it('이미 지난 일정은 바꿀 수 없다', () => {
    const edit = makeEdit('SCHEDULED', {
      ...CREW_SCHEDULE,
      crewSaleStartAt: '2026-11-20T10:00:00',
    })
    expect(validate(edit, { crewSaleStartAt: '2026-11-30 10:00' })).toContainEqual({
      path: 'crewSaleStartAt',
      message: SALE_CHANGE_ERRORS.scheduleLocked,
    })
  })

  it('아직 오지 않은 일정을 지난 시각으로 옮길 수 없다', () => {
    const edit = makeEdit('SCHEDULED', CREW_SCHEDULE)
    expect(validate(edit, { applicationCloseAt: '2026-11-21 11:00' })).toContainEqual({
      path: 'applicationCloseAt',
      message: SALE_CHANGE_ERRORS.scheduleInPast,
    })
  })

  it('판매 시작일을 오늘로부터 이틀 뒤보다 앞으로 옮길 수 없다', () => {
    const edit = makeEdit('SCHEDULED', PLAIN_SCHEDULE)
    const [issue] = validate(edit, { individualOpenAt: '2026-11-22 10:00' })
    expect(issue).toMatchObject({ path: 'individualOpenAt' })
    expect(issue.message).toContain('이틀 뒤')
  })

  it('판매가 시작된 대회는 크루 선예매를 열거나 닫을 수 없다', () => {
    const plain = makeEdit('ON_SALE', PLAIN_SCHEDULE)
    expect(
      validate(plain, {
        crewOpen: true,
        crewSaleStartAt: '2026-12-01 10:00',
        crewSaleEndAt: '2026-12-02 10:00',
      }),
    ).toContainEqual({ path: 'crewOpen', message: SALE_CHANGE_ERRORS.crewOpenLocked })
    const crew = makeEdit('ON_SALE', CREW_SCHEDULE)
    expect(validate(crew, { crewOpen: false })).toContainEqual({
      path: 'crewOpen',
      message: SALE_CHANGE_ERRORS.crewOpenLocked,
    })
  })
})

describe('일정 순서 (등록 화면과 같은 규칙)', () => {
  const edit = makeEdit('SCHEDULED', CREW_SCHEDULE)

  it('크루 예매 마감은 시작보다 늦어야 한다', () => {
    expect(validate(edit, { crewSaleEndAt: '2026-11-30 10:00' })).toContainEqual({
      path: 'crewSaleEndAt',
      message: SALE_CHANGE_ERRORS.crewEndAfterStart,
    })
  })

  it('일반 판매는 크루 예매 마감 다음 날 0시부터 시작할 수 있다', () => {
    expect(validate(edit, { individualOpenAt: '2026-12-04 12:00' })).toContainEqual({
      path: 'individualOpenAt',
      message: SALE_CHANGE_ERRORS.individualAfterCrew,
    })
  })

  it('접수 마감은 일반 판매 시작보다 늦어야 하고 대회 시작 전이어야 한다', () => {
    expect(validate(edit, { applicationCloseAt: '2026-12-05 00:00' })).toContainEqual({
      path: 'applicationCloseAt',
      message: SALE_CHANGE_ERRORS.closeAfterOpen,
    })
    expect(validate(edit, { applicationCloseAt: '2026-12-21 09:00' })).toContainEqual({
      path: 'applicationCloseAt',
      message: SALE_CHANGE_ERRORS.closeBeforeRace,
    })
  })

  it('열지 않기면 크루 예매 칸은 검사하지 않는다', () => {
    const plain = makeEdit('SCHEDULED', PLAIN_SCHEDULE)
    expect(validate(plain, { crewSaleStartAt: '', crewSaleEndAt: 'x' })).toEqual([])
  })
})

describe('크루 선예매를 열지 않을 때', () => {
  it('크루 정원이 있는 종목은 일반 정원이 1명 이상이어야 한다', () => {
    const edit = makeEdit('SCHEDULED', CREW_SCHEDULE, 20)
    const noIndividual = {
      crewOpen: false,
      crewSaleStartAt: '',
      crewSaleEndAt: '',
      courses: [{ individualQuota: '0', price: '45000' }],
    }
    expect(validate(edit, noIndividual)).toContainEqual({
      path: 'courses.0.individualQuota',
      message: SALE_CHANGE_ERRORS.quotaSum,
    })
  })

  it('요청에서 크루 예매 기간은 빼고 모든 종목의 크루 정원은 0으로 보낸다', () => {
    const edit = makeEdit('SCHEDULED', CREW_SCHEDULE, 20)
    const request = toUpdateRequest(edit, {
      ...toSaleChangeValues(edit),
      crewOpen: false,
      crewSaleStartAt: '',
      crewSaleEndAt: '',
    })
    expect(request.crewSaleStartAt).toBeUndefined()
    expect(request.crewSaleEndAt).toBeUndefined()
    expect(request.courses.map((c) => c.crewQuota)).toEqual([0])
  })
})

describe('수정 요청의 일정 필드', () => {
  it('바꾸지 않은 일정은 저장된 값을 그대로 보낸다 (초 단위까지)', () => {
    const edit = makeEdit('ON_SALE', {
      ...CREW_SCHEDULE,
      crewSaleStartAt: '2026-11-18T10:00:00',
      applicationCloseAt: '2026-12-20T18:30:15',
    })
    const request = toUpdateRequest(edit, toSaleChangeValues(edit))
    expect(request).toMatchObject({
      crewSaleStartAt: '2026-11-18T10:00:00',
      crewSaleEndAt: '2026-12-04T23:59:59',
      individualOpenAt: '2026-12-05T00:00:00',
      applicationCloseAt: '2026-12-20T18:30:15',
    })
  })

  it('바꾼 일정은 서버 날짜·시각 형식으로 보낸다 (23:59는 그날 끝)', () => {
    const edit = makeEdit('SCHEDULED', CREW_SCHEDULE)
    const request = toUpdateRequest(edit, {
      ...toSaleChangeValues(edit),
      crewSaleEndAt: '2026-12-03 23:59',
      applicationCloseAt: '2026-12-19 09:30',
    })
    expect(request).toMatchObject({
      crewSaleEndAt: '2026-12-03T23:59:59',
      applicationCloseAt: '2026-12-19T09:30:00',
    })
  })

  it('열지 않기에서 열기로 바꾸면 입력한 크루 예매 기간을 보낸다', () => {
    const edit = makeEdit('SCHEDULED', PLAIN_SCHEDULE)
    const request = toUpdateRequest(edit, {
      ...toSaleChangeValues(edit),
      crewOpen: true,
      crewSaleStartAt: '2026-11-25 10:00',
      crewSaleEndAt: '2026-11-28 10:00',
    })
    expect(request).toMatchObject({
      crewSaleStartAt: '2026-11-25T10:00:00',
      crewSaleEndAt: '2026-11-28T10:00:00',
    })
  })
})

describe('읽기 전용 일정', () => {
  it('지난 일정과 판매 시작 뒤 바꿀 수 없는 일정을 잠근다', () => {
    const edit = makeEdit('ON_SALE', {
      crewSaleStartAt: '2026-11-18T10:00:00',
      crewSaleEndAt: '2026-11-25T23:59:59',
      individualOpenAt: '2026-11-26T00:00:00',
      applicationCloseAt: '2026-12-20T18:00:00',
    })
    expect(scheduleLocks(edit, NOW)).toEqual({
      crewOpen: true,
      crewSaleStartAt: true,
      crewSaleEndAt: false,
      individualOpenAt: false,
      applicationCloseAt: false,
    })
  })

  it('판매 예정이고 일정이 모두 오지 않았으면 아무것도 잠그지 않는다', () => {
    expect(scheduleLocks(makeEdit('SCHEDULED', CREW_SCHEDULE), NOW)).toEqual({
      crewOpen: false,
      crewSaleStartAt: false,
      crewSaleEndAt: false,
      individualOpenAt: false,
      applicationCloseAt: false,
    })
  })
})
