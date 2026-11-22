import { http, HttpResponse } from 'msw'

import type { productModel } from '@/shared/api'

import { findMockRace } from './races'

// 홈 배너 목 (CRU-146). 백엔드(PR #781, HomeBannerUseCase)와 같은 규칙으로 목록 전체를 바꾸고,
// 공개 배너에서는 홈에 안 보일 대회를 뺀다.
// 이번 주 접수 마감(2) · 접수 중(3) · 오픈 예정(7) · 접수 마감이라 홈에 안 보임(10)이 섞이게 고른다
const INITIAL_IDS = [2, 3, 7, 10]
const MAX = 5
// 대표 이미지가 없는 배너(색 포스터)를 볼 수 있게 7번은 이미지를 비운다
const NO_IMAGE_IDS = new Set([7])

// API 라우트와 서버 컴포넌트가 이 모듈을 따로 불러도 같은 목록을 보도록 전역에 둔다
const store = globalThis as typeof globalThis & { __mockHomeBannerIds?: number[] }
store.__mockHomeBannerIds ??= [...INITIAL_IDS]
const bannerIds = () => store.__mockHomeBannerIds!

type Hidden = Pick<productModel.HomeBannerAdminDto, 'hiddenReason' | 'hiddenReasonLabel'>

/** 백엔드 Race.bannerHiddenReason과 같은 순서(취소 → 판매 종료 → 판매 중지 → 접수 마감)로 이유 하나 */
function hiddenOf(race: { saleStatus?: string; applicationCloseAt?: string }): Hidden {
  if (race.saleStatus === 'CANCELED')
    return { hiddenReason: 'CANCELED', hiddenReasonLabel: '대회 취소' }
  if (race.saleStatus === 'CLOSED')
    return { hiddenReason: 'CLOSED', hiddenReasonLabel: '판매 종료' }
  if (race.saleStatus === 'STOPPED')
    return { hiddenReason: 'STOPPED', hiddenReasonLabel: '판매 중지' }
  const closeAt = race.applicationCloseAt
  if (closeAt && new Date(`${closeAt}+09:00`).getTime() <= Date.now()) {
    return { hiddenReason: 'APPLICATION_CLOSED', hiddenReasonLabel: '접수 마감' }
  }
  return { hiddenReason: null, hiddenReasonLabel: null }
}

function adminRow(raceId: number, displayOrder: number): productModel.HomeBannerAdminDto {
  const { detail } = findMockRace(raceId)!
  const hidden = hiddenOf(detail)
  return {
    raceId,
    title: detail.title ?? '',
    saleStatus: detail.saleStatus ?? 'ON_SALE',
    applicationCloseAt: detail.applicationCloseAt ?? '',
    displayOrder,
    visible: hidden.hiddenReason === null,
    ...hidden,
  }
}

const fail = (status: number, message: string, code?: string) =>
  HttpResponse.json({ status, ...(code && { code }), message, data: null }, { status })

export const homeBannerHandlers = [
  http.get('*/api/v1/product/banners', () =>
    HttpResponse.json({
      status: 200,
      message: '홈 배너를 조회했습니다.',
      data: bannerIds()
        .map((id) => findMockRace(id)!.summary)
        .filter((race) => hiddenOf(race).hiddenReason === null)
        .map((race) => (NO_IMAGE_IDS.has(race.raceId ?? 0) ? { ...race, imageUrl: null } : race)),
    }),
  ),
  http.get('*/api/v1/product/admin/banners', () =>
    HttpResponse.json({
      status: 200,
      message: '홈 배너 목록을 조회했습니다.',
      data: bannerIds().map(adminRow),
    }),
  ),
  http.put('*/api/v1/product/admin/banners', async ({ request }) => {
    const body = (await request.json().catch(() => null)) as { raceIds?: unknown } | null
    const ids = body?.raceIds
    if (!Array.isArray(ids) || !ids.every((id) => Number.isInteger(id) && id > 0)) {
      return fail(400, '배너로 고른 대회 목록을 보내주세요.', 'INVALID_REQUEST')
    }
    if (ids.length > MAX) return fail(400, '홈 배너는 최대 5개까지 고를 수 있습니다.')
    if (new Set(ids).size !== ids.length) {
      return fail(400, '같은 대회를 홈 배너에 두 번 넣을 수 없습니다.')
    }
    const races = (ids as number[]).map((id) => findMockRace(id))
    if (races.some((race) => !race)) return fail(404, '존재하지 않는 대회입니다.', 'RACE_NOT_FOUND')
    // 백엔드처럼 보낸 목록 전체를 따진다(이미 올라 있던 대회도)
    for (const race of races) {
      const status = race!.detail.saleStatus
      if (status === 'CANCELED') return fail(409, '취소된 대회는 배너로 고를 수 없습니다.')
      if (status === 'CLOSED') return fail(409, '판매가 끝난 대회는 배너로 고를 수 없습니다.')
    }
    store.__mockHomeBannerIds = ids as number[]
    return HttpResponse.json({
      status: 200,
      message: '홈 배너를 저장했습니다.',
      data: bannerIds().map(adminRow),
    })
  }),
]
