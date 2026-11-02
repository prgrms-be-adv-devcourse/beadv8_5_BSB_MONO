import { describe, expect, it } from 'vitest'

import { toFunnelView, type FunnelStepStat } from './funnels'

const step = (event: FunnelStepStat['event'], users: number): FunnelStepStat => ({
  event,
  label: event,
  users,
  previousUsers: 0,
})

describe('toFunnelView', () => {
  it('앞 단계 대비 비율을 붙이고 가장 많이 빠진 단계를 표시한다', () => {
    const view = toFunnelView([
      step('view_item', 100),
      step('add_to_cart', 40),
      step('begin_checkout', 30),
    ])

    expect(view.map((s) => s.rate)).toEqual([undefined, 0.4, 0.75])
    expect(view.map((s) => s.biggestDrop)).toEqual([false, true, false])
  })

  it('앞 단계가 0명이면 비율이 없고, 아무도 빠지지 않았으면 강조하지 않는다', () => {
    const view = toFunnelView([step('view_item', 0), step('add_to_cart', 0)])

    expect(view.map((s) => s.rate)).toEqual([undefined, undefined])
    expect(view.some((s) => s.biggestDrop)).toBe(false)
  })
})
