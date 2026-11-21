import { describe, expect, it } from 'vitest'

import { productMock } from '@/shared/testing'

import { SELLER_SALE_STATUS_TEXT, toAdminRaceRow, toRaceEditRequest } from './seller-race'

const race = productMock.getFindAdminRacesResponseMock().data![0]

describe('관리자 대회 한 줄', () => {
  it('판매자 이름 · 판매/정원 · 접수 기간 · 환불 기준일을 화면 문구로 바꾼다', () => {
    expect(toAdminRaceRow(race)).toMatchObject({
      id: 1,
      seller: '(주)러닝컴퍼니',
      sold: 15,
      capacity: 600,
      saleStatus: 'ON_SALE',
      badge: { label: '판매 중' },
      raceDateTime: '11/1 (일) 08:00',
      applicationPeriod: '10/20 (화) 09:00 ~ 10/31 (토) 18:00',
      refundDeadline: '10/25 (일)',
    })
  })

  it('크루 예매가 있으면 접수 기간은 크루 예매 시작부터다', () => {
    const row = toAdminRaceRow({ ...race, crewSaleStartAt: '2026-10-18T10:00:00' })
    expect(row.applicationPeriod).toBe('10/18 (일) 10:00 ~ 10/31 (토) 18:00')
  })
})

const course = (minAge: number | undefined, maxAge?: number) => ({
  raceCourseId: 1,
  name: '10K',
  minAge,
  maxAge,
})

function ages(courses: Parameters<typeof toRaceEditRequest>[0]['courses']) {
  const request = toRaceEditRequest({ courses }, [], {})
  return request.courses.map(({ minAge, maxAge }) => ({ minAge, maxAge }))
}

describe('toRaceEditRequest 종목 나이', () => {
  it('받아 온 최소 나이가 19 이상이면 성인만(19), 아니면 제한 없음(14)으로 바꾸고 최대 나이는 보내지 않는다', () => {
    expect(
      ages([course(19), course(25, 70), course(16, 80), course(14), course(undefined)]),
    ).toEqual([
      { minAge: 19, maxAge: undefined },
      { minAge: 19, maxAge: undefined },
      { minAge: 14, maxAge: undefined },
      { minAge: 14, maxAge: undefined },
      { minAge: 14, maxAge: undefined },
    ])
  })
})

describe('toRaceEditRequest 크루 인원', () => {
  it('크루 최소 인원이 없으면 등록 화면과 같은 4명으로 보내고, 있으면 그대로 보낸다', () => {
    expect(toRaceEditRequest({ ...race, crewMinCount: undefined }, [], {}).crewMinCount).toBe(4)
    expect(toRaceEditRequest({ ...race, crewMinCount: 6 }, [], {}).crewMinCount).toBe(6)
  })
})

// 시안(대회 상세): 판매 중만 16 Medium 검정, 판매 예정·종료·중지는 12 보조색
describe('SELLER_SALE_STATUS_TEXT', () => {
  it('판매 중만 16 굵게 검정이다', () => {
    expect(SELLER_SALE_STATUS_TEXT.ON_SALE).toMatch(/text-body1-medium/)
    expect(SELLER_SALE_STATUS_TEXT.ON_SALE).toMatch(/text-text-primary/)
    expect(SELLER_SALE_STATUS_TEXT.SCHEDULED).toBe('text-text-secondary')
    expect(SELLER_SALE_STATUS_TEXT.STOPPED).not.toMatch(/body1/)
  })
})
