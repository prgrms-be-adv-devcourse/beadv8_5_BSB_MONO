import { describe, expect, it } from 'vitest'

import { fileMock, productMock } from '@/shared/testing'

import {
  applicationPhase,
  raceBadge,
  toClosingSoonCard,
  toRaceDetail,
  toRaceListItem,
} from './mappers'

const summary = productMock.getSearchResponseMock().data!.races![0]
const detail = productMock.getFindRaceResponseMock().data!

// 목 예시: 개인 예매 오픈 2026-10-20 09:00, 접수 마감 2026-10-31 18:00 (한국 시간)
const BEFORE_OPEN = new Date('2026-10-20T08:59:59+09:00')
const OPEN = new Date('2026-10-20T09:00:00+09:00')
const AFTER_CLOSE = new Date('2026-10-31T18:00:00+09:00')

describe('대회 응답 → 화면 모양', () => {
  it('목록 한 줄: 대회일 · 장소, 최저가, 남은 자리', () => {
    expect(toRaceListItem(summary)).toMatchObject({
      id: '1',
      title: '2026 용감한 쿠키RUN in 서울',
      posterLabel: '10K',
      meta: '11/1 (일) · 서울숲 일대',
      price: '₩45,000~',
      remaining: '580자리 남음',
    })
  })

  it('목록 응답에 imageUrl이 있을 때만 카드 이미지가 붙는다', () => {
    expect(toRaceListItem(summary).image).toBeUndefined()
    expect(toRaceListItem({ ...summary, imageUrl: '/mock/races/a.png' }).image).toBe(
      '/mock/races/a.png',
    )
  })

  it('곧 마감 카드: 대회마다 한 장, 남은 날 · 남은 자리, 대회일 · 장소, 최저가부터', () => {
    expect(toClosingSoonCard(summary, OPEN)).toMatchObject({
      id: '1',
      title: '2026 용감한 쿠키RUN in 서울',
      posterLabel: '10K',
      status: 'D-11 · 580자리 남음',
      meta: '11/1 (일) · 서울숲 일대',
      price: '45,000원부터',
    })
  })

  it('곧 마감 카드: 대회일이 없으면 장소만, 마감일이 없으면 남은 자리만', () => {
    const card = toClosingSoonCard(
      { ...summary, raceDate: undefined, applicationCloseAt: undefined },
      OPEN,
    )
    expect(card.meta).toBe('서울숲 일대')
    expect(card.status).toBe('580자리 남음')
  })

  it('신청 기간: 개인 예매 오픈 전 · 접수 중 · 접수 마감 뒤', () => {
    expect(applicationPhase(summary, BEFORE_OPEN)).toBe('upcoming')
    expect(applicationPhase(summary, OPEN)).toBe('open')
    expect(applicationPhase(summary, AFTER_CLOSE)).toBe('closed')
  })

  it('판매 상태가 판매 중이어도 개인 예매 오픈 전이면 오픈 예정', () => {
    expect(summary.saleStatus).toBe('ON_SALE')
    expect(raceBadge(summary, BEFORE_OPEN)).toEqual({ label: '오픈 예정', tone: 'neutral' })
  })

  it('판매 중지 · 취소된 대회는 기간 안이어도 마감', () => {
    expect(applicationPhase({ ...summary, saleStatus: 'STOPPED' }, OPEN)).toBe('closed')
    expect(applicationPhase({ ...summary, saleStatus: 'CANCELED' }, OPEN)).toBe('closed')
  })

  it('배지: 접수 중 · 마감 임박 · 정원 마감 · 마감', () => {
    expect(raceBadge(summary, OPEN)).toEqual({ label: '접수 중', tone: 'brand' })
    expect(raceBadge(summary, new Date('2026-10-29T12:00:00+09:00'))).toEqual({
      label: '마감 임박',
      tone: 'warning',
    })
    expect(raceBadge({ ...summary, courses: [{ status: 'SOLD_OUT' }] }, OPEN).label).toBe(
      '정원 마감',
    )
    expect(raceBadge(summary, AFTER_CLOSE)).toEqual({ label: '마감', tone: 'neutral' })
  })

  it('상세: 신청 기간을 함께 넘긴다', () => {
    expect(toRaceDetail(detail, {}, BEFORE_OPEN).phase).toBe('upcoming')
    expect(toRaceDetail(detail, {}, OPEN).phase).toBe('open')
    expect(toRaceDetail(detail, {}, AFTER_CLOSE).phase).toBe('closed')
  })

  it('상세: 종목 ID를 종목 선택 값으로 쓰고, 종목 대표 이미지를 붙인다', () => {
    const files = fileMock.getFindByRefResponseMock().data!
    const race = toRaceDetail(detail, { 1: files })

    expect(race.options).toEqual([
      expect.objectContaining({ id: '1', name: '10K', price: 45000, image: files[0].url }),
    ])
    expect(race.images).toEqual([files[0].url, files[1].url])
    expect(race.startsAt).toBe('11/1 (일) 08:00 출발')
    expect(race.schedule).toContainEqual({ label: '전액 환불 기한', value: '10/31 (토) 23:59' })
    expect(race.seller.company).toBe('(주)쿠키런')
  })

  it('상세: 이미지가 없으면 빈 목록(색 포스터)', () => {
    expect(toRaceDetail(detail).images).toEqual([])
  })
})
