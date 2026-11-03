import { describe, expect, it } from 'vitest'

import { toFunnelView, type FunnelStepStat } from './funnels'

const step = (event: FunnelStepStat['event'], users: number): FunnelStepStat => ({
  event,
  label: event,
  users,
  previousUsers: 0,
})

describe('toFunnelView', () => {
  it('앞 단계 대비 비율과 첫 단계 기준 막대 길이를 붙인다', () => {
    const view = toFunnelView([
      step('view_item', 200),
      step('add_to_cart', 100),
      step('purchase', 30),
    ])

    expect(view.map((s) => s.rate)).toEqual([undefined, 0.5, 0.3])
    expect(view.map((s) => s.barRatio)).toEqual([1, 0.5, 0.15])
  })

  it('빠진 사람 수가 아니라 비율이 가장 낮은 단계를 강조한다', () => {
    // 1000→600은 400명이 빠지지만 60%, 600→100은 500명이 빠지고 17%, 100→10은 90명이 빠지지만 10%
    const view = toFunnelView([
      step('view_item', 1000),
      step('add_to_cart', 600),
      step('begin_checkout', 100),
      step('purchase', 10),
    ])

    expect(view.map((s) => s.biggestDrop)).toEqual([false, false, false, true])
  })

  it('기록이 없으면 비율·막대·강조가 없다', () => {
    const view = toFunnelView([step('view_item', 0), step('add_to_cart', 0)])

    expect(view.map((s) => s.rate)).toEqual([undefined, undefined])
    expect(view.map((s) => s.barRatio)).toEqual([0, 0])
    expect(view.some((s) => s.biggestDrop)).toBe(false)
  })
})
