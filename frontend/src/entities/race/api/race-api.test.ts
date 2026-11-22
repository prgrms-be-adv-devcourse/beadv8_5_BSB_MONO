import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '@/shared/testing'

import { fetchRaceDetail, fetchRacePage, raceCoverQueryOptions } from './race-api'

describe('fetchRaceDetail', () => {
  it('상품 API의 대회와 파일 API의 대회 대표 이미지를 합친다', async () => {
    const race = await fetchRaceDetail('1')
    expect(race?.title).toBe('2026 용감한 쿠키RUN in 서울')
    expect(race?.images[0]).toBe('/mock/races/cookierun.webp')
    expect(race?.options[0]).toMatchObject({ id: '1', image: '/mock/races/cookierun.webp' })
  })

  it('대표 이미지는 대회 파일(refType=Race, refId=대회 ID)에서 찾는다', async () => {
    server.use(
      http.get('*/api/v1/file/files', ({ request }) => {
        const params = new URL(request.url).searchParams
        const isRace = params.get('refType') === 'Race' && params.get('refId') === '1'
        const data = isRace ? [{ id: 9, fileType: 'THUMBNAIL', url: '/cover.webp' }] : []
        return HttpResponse.json({ status: 200, message: 'ok', data })
      }),
    )
    const race = await fetchRaceDetail('1')
    expect(race?.images).toEqual(['/cover.webp'])
  })

  it('없는 대회(404)면 null', async () => {
    server.use(
      http.get('*/api/v1/product/races/:raceId', () =>
        HttpResponse.json(
          { status: 404, message: '존재하지 않는 대회입니다.', data: null },
          { status: 404 },
        ),
      ),
    )
    await expect(fetchRaceDetail('999')).resolves.toBeNull()
  })

  it('숫자가 아닌 ID는 API를 부르지 않고 null', async () => {
    await expect(fetchRaceDetail('cookierun')).resolves.toBeNull()
  })

  it('이미지를 못 받아도 대회는 보여 준다', async () => {
    server.use(http.get('*/api/v1/file/files', () => HttpResponse.error()))
    const race = await fetchRaceDetail('1')
    expect(race?.images).toEqual([])
  })
})

describe('raceCoverQueryOptions', () => {
  it('대회 대표 이미지 주소 한 장을 준다', async () => {
    const { queryFn } = raceCoverQueryOptions(2)
    await expect((queryFn as () => Promise<string | null>)()).resolves.toBe(
      '/mock/races/youth10k.webp',
    )
  })

  it('못 받으면 null', async () => {
    server.use(http.get('*/api/v1/file/files', () => HttpResponse.error()))
    const { queryFn } = raceCoverQueryOptions(2)
    await expect((queryFn as () => Promise<string | null>)()).resolves.toBeNull()
  })
})

describe('fetchRacePage', () => {
  it('목록 한 번 요청으로 받은 imageUrl을 그대로 주고, 파일 서비스는 부르지 않는다', async () => {
    const fileRequests: string[] = []
    server.use(
      http.get('*/api/v1/product/races', () =>
        HttpResponse.json({
          status: 200,
          message: 'ok',
          data: {
            races: [
              { raceId: 7, title: '대회 7', imageUrl: '/seven.webp' },
              { raceId: 8, title: '대회 8', imageUrl: null },
            ],
            page: 0,
            size: 20,
          },
        }),
      ),
      http.get('*/api/v1/file/files', ({ request }) => {
        fileRequests.push(request.url)
        return HttpResponse.json({ status: 200, message: 'ok', data: [] })
      }),
    )
    const { races } = await fetchRacePage({ page: 0, size: 20 })
    expect(races?.map((r) => r.imageUrl)).toEqual(['/seven.webp', null])
    expect(fileRequests).toEqual([])
  })
})
