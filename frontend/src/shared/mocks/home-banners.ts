import { http, HttpResponse } from 'msw'

import type { productModel } from '@/shared/api'

import { findMockRace } from './races'

// 홈 배너 목 (CRU-146). 백엔드 계약대로 목록 전체를 바꾸고, 공개 배너에서는 홈에 안 보일 대회를 뺀다.
// 이번 주 접수 마감(2) · 접수 중(3) · 오픈 예정(7)이 섞이게 고른다
const INITIAL_IDS = [2, 3, 7]
const MAX = 5
// 대표 이미지가 없는 배너(색 포스터)를 볼 수 있게 7번은 이미지를 비운다
const NO_IMAGE_IDS = new Set([7])
const HIDDEN_STATUS = new Set(['STOPPED', 'CLOSED', 'CANCELED'])
const ENDED_STATUS = new Set(['CLOSED', 'CANCELED'])

let bannerIds = [...INITIAL_IDS]

function visibleOf(race: { saleStatus?: string; applicationCloseAt?: string }) {
  if (race.saleStatus && HIDDEN_STATUS.has(race.saleStatus)) return false
  const closeAt = race.applicationCloseAt
  return !closeAt || new Date(`${closeAt}+09:00`).getTime() > Date.now()
}

function adminRow(raceId: number): productModel.HomeBannerAdminDto {
  const { detail } = findMockRace(raceId)!
  return {
    raceId,
    title: detail.title ?? '',
    saleStatus: detail.saleStatus ?? 'ON_SALE',
    crewSaleStartAt: detail.crewSaleStartAt ?? null,
    crewSaleEndAt: detail.crewSaleEndAt ?? null,
    individualOpenAt: detail.individualOpenAt,
    applicationCloseAt: detail.applicationCloseAt,
    visible: visibleOf(detail),
  }
}

const fail = (status: number, code: string, message: string) =>
  HttpResponse.json({ status, code, message, data: null }, { status })

export const homeBannerHandlers = [
  http.get('*/api/v1/product/banners', () =>
    HttpResponse.json({
      status: 200,
      message: '홈 배너를 조회했습니다.',
      data: bannerIds
        .map((id) => findMockRace(id)!.summary)
        .filter(visibleOf)
        .map((race) => (NO_IMAGE_IDS.has(race.raceId ?? 0) ? { ...race, imageUrl: null } : race)),
    }),
  ),
  http.get('*/api/v1/product/admin/banners', () =>
    HttpResponse.json({
      status: 200,
      message: '홈 배너를 조회했습니다.',
      data: bannerIds.map(adminRow),
    }),
  ),
  http.put('*/api/v1/product/admin/banners', async ({ request }) => {
    const body = (await request.json().catch(() => null)) as { raceIds?: unknown } | null
    const ids = body?.raceIds
    if (
      !Array.isArray(ids) ||
      ids.length > MAX ||
      !ids.every((id) => Number.isInteger(id)) ||
      new Set(ids).size !== ids.length
    ) {
      return fail(400, 'INVALID_REQUEST', '배너는 겹치지 않게 5개까지 고를 수 있습니다.')
    }
    if (!ids.every((id: number) => findMockRace(id))) {
      return fail(404, 'RACE_NOT_FOUND', '대회를 찾을 수 없습니다.')
    }
    // 409는 새로 올리는 대회에만 따진다. 이미 올라 있던 대회가 나중에 끝나도 빼기 · 순서 바꾸기는 막지 않는다
    const ended = (ids as number[]).some(
      (id) =>
        !bannerIds.includes(id) && ENDED_STATUS.has(findMockRace(id)!.detail.saleStatus ?? ''),
    )
    if (ended) {
      return fail(
        409,
        'HOME_BANNER_RACE_ENDED',
        '취소되었거나 판매가 끝난 대회는 배너에 올릴 수 없습니다.',
      )
    }
    bannerIds = ids as number[]
    return HttpResponse.json({
      status: 200,
      message: '홈 배너를 저장했습니다.',
      data: bannerIds.map(adminRow),
    })
  }),
]
