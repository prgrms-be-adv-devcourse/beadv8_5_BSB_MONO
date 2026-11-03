// @vitest-environment node
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { fetchRaceDetail } from '@/entities/race'
import { server } from '@/shared/testing'

vi.hoisted(() => {
  // server.use로 바꾼 실패 응답을 쓰려고 실제 API 모드로 고정한다
  process.env.API_MOCKING = 'off'
})

const { generateMetadata } = await import('./page')

function metadataFor(raceId: string) {
  return generateMetadata(
    { params: Promise.resolve({ raceId }), searchParams: Promise.resolve({}) },
    // 부모 메타데이터는 쓰지 않는다
    undefined as never,
  )
}

describe('대회 상세 메타데이터', () => {
  it('대회 이름을 제목에, 날짜 · 장소를 설명에 넣는다', async () => {
    const race = await fetchRaceDetail('1')
    if (!race) throw new Error('목 대회 1이 없습니다')

    const metadata = await metadataFor('1')

    expect(metadata.title).toBe(race.title)
    expect(metadata.description).toContain(race.startsAt)
    expect(metadata.description).toContain('러닝 대회 참가권')
    expect(metadata.openGraph?.title).toBe(race.title)
  })

  it('대회를 불러오지 못하면 기본 메타데이터를 쓴다', async () => {
    server.use(
      http.get('*/api/v1/product/races/:raceId', () =>
        HttpResponse.json({ status: 500 }, { status: 500 }),
      ),
    )

    expect(await metadataFor('1')).toEqual({})
  })
})
