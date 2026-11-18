import { describe, expect, it } from 'vitest'

import { toRaceEditRequest } from './seller-race'

const course = (minAge: number | undefined, maxAge?: number) => ({
  raceCourseId: 1,
  name: '10K',
  minAge,
  maxAge,
})

function ages(courses: Parameters<typeof toRaceEditRequest>[0]['courses']) {
  const request = toRaceEditRequest({ courses }, [], {})
  return request.courses.map(({ minAge, maxAge }) => ({ minAge, maxAge }))
}

describe('toRaceEditRequest 종목 나이', () => {
  it('받아 온 최소 나이가 19 이상이면 성인만(19), 아니면 제한 없음(14)으로 바꾸고 최대 나이는 보내지 않는다', () => {
    expect(
      ages([course(19), course(25, 70), course(16, 80), course(14), course(undefined)]),
    ).toEqual([
      { minAge: 19, maxAge: undefined },
      { minAge: 19, maxAge: undefined },
      { minAge: 14, maxAge: undefined },
      { minAge: 14, maxAge: undefined },
      { minAge: 14, maxAge: undefined },
    ])
  })
})
