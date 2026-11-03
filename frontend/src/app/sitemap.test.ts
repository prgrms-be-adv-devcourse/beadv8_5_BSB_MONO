// @vitest-environment node
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { server } from '@/shared/testing'

vi.hoisted(() => {
  // CI는 API_MOCKING=on으로 돈다. 그러면 기본 핸들러로 바로 가서 server.use로 바꾼 응답을 쓰지 않는다
  process.env.API_MOCKING = 'off'
  process.env.SITE_URL = 'https://crewrun.example'
})

const { default: sitemap } = await import('./sitemap')

const SEARCH = '*/api/v1/product/races'

function searchReturns(races: { raceId: number; saleStatus: string }[]) {
  server.use(
    http.get(SEARCH, () =>
      HttpResponse.json({
        status: 200,
        message: 'ok',
        data: { races, page: 0, size: 100, totalElements: races.length, totalPages: 1 },
      }),
    ),
  )
}

describe('sitemap', () => {
  it('홈 · 대회 목록과 판매 중인 대회 상세만 넣는다', async () => {
    searchReturns([
      { raceId: 1, saleStatus: 'ON_SALE' },
      { raceId: 2, saleStatus: 'SCHEDULED' },
      { raceId: 3, saleStatus: 'ON_SALE' },
    ])

    const urls = (await sitemap()).map((entry) => entry.url)

    expect(urls).toEqual([
      'https://crewrun.example',
      'https://crewrun.example/races',
      'https://crewrun.example/races/1',
      'https://crewrun.example/races/3',
    ])
  })

  it('대회 목록 API가 실패해도 고정 주소는 돌려준다', async () => {
    server.use(http.get(SEARCH, () => HttpResponse.json({ status: 500 }, { status: 500 })))

    const urls = (await sitemap()).map((entry) => entry.url)

    expect(urls).toEqual(['https://crewrun.example', 'https://crewrun.example/races'])
  })
})
