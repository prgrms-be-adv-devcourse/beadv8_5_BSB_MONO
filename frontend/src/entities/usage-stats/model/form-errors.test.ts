import { describe, expect, it } from 'vitest'

import { rankFormErrors } from './form-errors'

describe('rankFormErrors', () => {
  it('쉼표로 이은 묶음을 하나씩 나눠 더하고 상위 3개를 고른다', () => {
    expect(
      rankFormErrors([
        { value: '종목 · 참가권', users: 6 },
        { value: '기본 정보,이미지', users: 4 },
        { value: '이미지', users: 3 },
        { value: '키트 배송,종목 · 참가권', users: 1 },
      ]),
    ).toEqual([
      { name: '이미지', users: 7 },
      { name: '종목 · 참가권', users: 7 },
      { name: '기본 정보', users: 4 },
    ])
  })

  it('값이 없는 기록((not set))은 세지 않는다', () => {
    expect(rankFormErrors([{ value: '(not set)', users: 5 }])).toEqual([])
  })
})
