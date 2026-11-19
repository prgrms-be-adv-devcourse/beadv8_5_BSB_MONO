import { describe, expect, it } from 'vitest'

import { oldestDay, todoSummary } from './todo-summary'

describe('todoSummary', () => {
  it('셀 수 없는 줄은 숫자를 만들지 않는다', () => {
    expect(todoSummary(null)).toEqual({ kind: 'link' })
  })
  it('아직 못 불러왔으면 숫자를 만들지 않는다', () => {
    expect(todoSummary(undefined)).toEqual({ kind: 'link' })
  })
  it('0이면 없음', () => {
    expect(todoSummary(0)).toEqual({ kind: 'none' })
  })
  it('1 이상이면 건수와 가장 오래된 날', () => {
    expect(todoSummary(3, '2026-11-02T09:00:00')).toEqual({
      kind: 'count',
      count: 3,
      oldest: '11/2',
    })
    expect(todoSummary(2)).toEqual({ kind: 'count', count: 2, oldest: undefined })
  })
})

describe('oldestDay', () => {
  it('가장 이른 시각을 고른다', () => {
    expect(oldestDay(['2026-11-05T01:00:00', '2026-11-02T09:00:00'])).toBe('2026-11-02T09:00:00')
    expect(oldestDay([])).toBeUndefined()
  })
})
