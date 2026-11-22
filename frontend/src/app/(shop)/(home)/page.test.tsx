import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import type { RaceSummary } from '@/entities/race'
import { server } from '@/shared/testing'

import Page from './page'

type HomeProps = { closingSoon: RaceSummary[]; newRaces: RaceSummary[] }

async function renderPage() {
  const element = (await Page({ searchParams: Promise.resolve({}) } as never)) as {
    props: HomeProps
  }
  return element.props
}

describe('홈 페이지 데이터', () => {
  it('새로 등록된 대회는 최신 등록순(LATEST) 첫 페이지를 8개 받는다', async () => {
    const requested: URLSearchParams[] = []
    server.events.on('request:start', ({ request }) => {
      const url = new URL(request.url)
      if (url.pathname.endsWith('/product/races')) requested.push(url.searchParams)
    })
    await renderPage()
    server.events.removeAllListeners()
    const latest = requested.find((params) => params.get('sort') === 'LATEST')
    expect(latest?.get('page')).toBe('0')
    expect(latest?.get('size')).toBe('8')
  })

  it('곧 마감돼요에 나온 대회는 새로 등록된 대회에서 빼고, 4개를 넘기지 않는다', async () => {
    const { closingSoon, newRaces } = await renderPage()
    const closingIds = closingSoon.map((race) => race.id)
    expect(newRaces.length).toBeGreaterThan(0)
    expect(newRaces.length).toBeLessThanOrEqual(4)
    expect(newRaces.some((race) => closingIds.includes(race.id))).toBe(false)
  })

  it('새로 등록된 대회를 못 받아도 홈은 그린다 (그 영역만 숨긴다)', async () => {
    server.use(
      http.get('*/api/v1/product/races', ({ request }) =>
        new URL(request.url).searchParams.get('sort') === 'LATEST'
          ? HttpResponse.error()
          : undefined,
      ),
    )
    const { closingSoon, newRaces } = await renderPage()
    expect(newRaces).toEqual([])
    expect(closingSoon.length).toBeGreaterThan(0)
  })
})
