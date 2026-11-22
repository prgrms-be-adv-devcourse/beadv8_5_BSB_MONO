import { describe, expect, it } from 'vitest'

import type { productModel } from '@/shared/api'

import { toBannerCandidate, toHomeBanner, toHomeBannerRow } from './home-banner'

// 2026-11-22 (일) 12:00 한국 시간
const NOW = new Date('2026-11-22T12:00:00+09:00')

const SUMMARY: productModel.RaceSummaryDto = {
  raceId: 1,
  title: '2026 용감한 쿠키RUN in 서울',
  location: '서울숲 일대',
  saleStatus: 'ON_SALE',
  crewSaleStartAt: null,
  crewSaleEndAt: null,
  individualOpenAt: '2026-11-20T09:00:00',
  applicationCloseAt: '2026-12-20T18:00:00',
  courses: [{ name: '5K' }, { name: '10K' }],
  imageUrl: '/a.webp',
}

describe('toHomeBanner', () => {
  it('접수 중이면 마감일 · 종목 · 장소를 한 줄로 보여 준다', () => {
    expect(toHomeBanner(SUMMARY, NOW)).toEqual({
      id: '1',
      title: '2026 용감한 쿠키RUN in 서울',
      image: '/a.webp',
      posterLabel: '5K · 10K',
      chip: '접수 중',
      description: '12/20까지 · 5K · 10K · 서울숲 일대',
    })
  })

  it('접수 마감이 7일 안이면 이번 주 접수 마감 칩을 단다', () => {
    const banner = toHomeBanner({ ...SUMMARY, applicationCloseAt: '2026-11-27T18:00:00' }, NOW)
    expect(banner.chip).toBe('이번 주 접수 마감')
    expect(banner.description).toBe('11/27까지 · 5K · 10K · 서울숲 일대')
  })

  it('크루 예매 기간이면 크루 예매 중 칩과 크루 예매 마감일을 보여 준다', () => {
    const banner = toHomeBanner(
      {
        ...SUMMARY,
        crewSaleStartAt: '2026-11-21T10:00:00',
        crewSaleEndAt: '2026-11-28T18:00:00',
        individualOpenAt: '2026-11-29T10:00:00',
      },
      NOW,
    )
    expect(banner.chip).toBe('크루 예매 중')
    expect(banner.description).toBe('11/28까지 · 5K · 10K · 서울숲 일대')
  })

  it('개인 예매 오픈 전이면 오픈 예정 칩과 오픈 일시를 보여 준다', () => {
    const banner = toHomeBanner({ ...SUMMARY, individualOpenAt: '2026-12-01T10:00:00' }, NOW)
    expect(banner.chip).toBe('오픈 예정')
    expect(banner.description).toBe('12/1 (화) 10:00 오픈 · 5K · 10K · 서울숲 일대')
  })

  it('접수 마감이 7일 뒤면 이번 주, 8일 뒤면 접수 중이다', () => {
    expect(toHomeBanner({ ...SUMMARY, applicationCloseAt: '2026-11-29T18:00:00' }, NOW).chip).toBe(
      '이번 주 접수 마감',
    )
    expect(toHomeBanner({ ...SUMMARY, applicationCloseAt: '2026-11-30T18:00:00' }, NOW).chip).toBe(
      '접수 중',
    )
  })

  it('개인 예매 오픈 시각과 같은 순간부터 접수 중이다', () => {
    expect(toHomeBanner({ ...SUMMARY, individualOpenAt: '2026-11-22T12:00:00' }, NOW).chip).toBe(
      '접수 중',
    )
  })

  it('대표 이미지가 없으면 image가 비고, 장소가 없으면 장소를 빼고 잇는다', () => {
    const banner = toHomeBanner({ ...SUMMARY, imageUrl: null, location: undefined }, NOW)
    expect(banner.image).toBeUndefined()
    expect(banner.description).toBe('12/20까지 · 5K · 10K')
  })
})

const ADMIN: productModel.HomeBannerAdminDto = {
  raceId: 3,
  title: '2026 서울마라톤',
  saleStatus: 'ON_SALE',
  applicationCloseAt: '2026-12-10T18:00:00',
  displayOrder: 0,
  visible: true,
  hiddenReason: null,
  hiddenReasonLabel: null,
}

describe('toHomeBannerRow', () => {
  it('홈에 보이는 대회는 판매 상태와 접수 마감일을 보여 준다', () => {
    expect(toHomeBannerRow(ADMIN)).toEqual({
      raceId: 3,
      title: '2026 서울마라톤',
      stage: '판매 중',
      closeDate: '12/10',
      visible: true,
    })
    expect(toHomeBannerRow({ ...ADMIN, saleStatus: 'SCHEDULED' }).stage).toBe('판매 예정')
  })

  it('홈에 안 보이는 대회는 서버가 준 이유 이름을 붙인다', () => {
    const hidden = (hiddenReason: productModel.HomeBannerAdminDto['hiddenReason'], label: string) =>
      toHomeBannerRow({ ...ADMIN, visible: false, hiddenReason, hiddenReasonLabel: label }).stage
    expect(hidden('APPLICATION_CLOSED', '접수 마감')).toBe('접수 마감 · 홈에 안 보임')
    expect(hidden('STOPPED', '판매 중지')).toBe('판매 중지 · 홈에 안 보임')
    expect(hidden('CLOSED', '판매 종료')).toBe('판매 종료 · 홈에 안 보임')
    expect(hidden('CANCELED', '대회 취소')).toBe('대회 취소 · 홈에 안 보임')
  })

  it('이유 이름이 비어 오면 홈에 안 보임만 쓴다', () => {
    expect(toHomeBannerRow({ ...ADMIN, visible: false }).stage).toBe('홈에 안 보임')
  })
})

const SELLER_RACE: productModel.SellerRaceDto = {
  raceId: 5,
  title: '부산바다마라톤',
  saleStatus: 'ON_SALE',
  crewSaleStartAt: null,
  individualOpenAt: '2026-11-20T09:00:00',
  applicationCloseAt: '2026-12-14T18:00:00',
}

describe('toBannerCandidate', () => {
  it('배너에 올릴 수 있는 대회는 단계와 마감일을 한 줄로 보여 준다', () => {
    expect(toBannerCandidate(SELLER_RACE, NOW)).toEqual({
      raceId: 5,
      title: '부산바다마라톤',
      meta: '접수 중 · 12/14 마감',
    })
  })

  it('크루 예매 마감이 없으면 개인 예매 오픈 전까지를 크루 예매 기간으로 본다', () => {
    const crew = {
      ...SELLER_RACE,
      crewSaleStartAt: '2026-11-21T10:00:00',
      individualOpenAt: '2026-11-25T10:00:00',
    }
    expect(toBannerCandidate(crew, NOW)?.meta).toBe('크루 예매 중 · 12/14 마감')
    expect(toBannerCandidate(crew, new Date('2026-11-25T10:00:00+09:00'))?.meta).toBe(
      '접수 중 · 12/14 마감',
    )
  })

  it('접수가 끝났거나 판매 중지 · 종료 · 취소된 대회는 고를 수 없다', () => {
    expect(
      toBannerCandidate({ ...SELLER_RACE, applicationCloseAt: '2026-11-21T18:00:00' }, NOW),
    ).toBe(undefined)
    for (const saleStatus of ['STOPPED', 'CLOSED', 'CANCELED'] as const) {
      expect(toBannerCandidate({ ...SELLER_RACE, saleStatus }, NOW)).toBeUndefined()
    }
  })
})
