import { describe, expect, it } from 'vitest'

import { productMock } from '@/shared/testing'

import { NEW_RACES_PARAMS, pickNewRaces } from './new-races'

const base = productMock.getSearchResponseMock().data!.races![0]
const races = (n: number) => Array.from({ length: n }, (_, i) => ({ ...base, raceId: i + 1 }))
const NOW = new Date('2026-10-20T09:00:00+09:00')

describe('pickNewRaces', () => {
  it('서버가 준 순서(최신 등록순) 그대로 4개만 카드로 만든다', () => {
    expect(pickNewRaces(races(8), [], NOW).map((card) => card.id)).toEqual(['1', '2', '3', '4'])
  })

  it('곧 마감돼요에 나온 대회는 빼고 4개를 채운다', () => {
    expect(pickNewRaces(races(8), ['1', '3'], NOW).map((card) => card.id)).toEqual([
      '2',
      '4',
      '5',
      '6',
    ])
  })

  it('빼고 나서 모자라면 있는 만큼만 주고, 하나도 없으면 빈 목록이다', () => {
    expect(pickNewRaces(races(3), ['1'], NOW)).toHaveLength(2)
    expect(pickNewRaces(races(2), ['1', '2'], NOW)).toEqual([])
    expect(pickNewRaces([], [], NOW)).toEqual([])
  })

  it('접수가 이미 끝난 대회는 보여 주지 않는다', () => {
    expect(pickNewRaces(races(2), [], new Date('2026-11-01T00:00:00+09:00'))).toEqual([])
  })

  it('요청: 최신 등록순 첫 페이지를 겹칠 몫까지 8개 받는다', () => {
    expect(NEW_RACES_PARAMS).toEqual({ sort: 'LATEST', page: 0, size: 8 })
  })
})
