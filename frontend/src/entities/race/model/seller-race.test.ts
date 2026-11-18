import { describe, expect, it } from 'vitest'

import { productMock } from '@/shared/testing'

import { toAdminRaceRow } from './seller-race'

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
