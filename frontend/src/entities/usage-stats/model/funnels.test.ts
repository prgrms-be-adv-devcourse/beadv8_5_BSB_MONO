import { describe, expect, it } from 'vitest'

import { FUNNELS, toFunnelView, type FunnelStepStat } from './funnels'

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
      step('begin_checkout', 100),
      step('purchase', 30),
    ])

    expect(view.map((s) => s.rate)).toEqual([undefined, 0.5, 0.3])
    expect(view.map((s) => s.barRatio)).toEqual([1, 0.5, 0.15])
  })

  it('빠진 사람 수가 아니라 비율이 가장 낮은 단계를 강조한다', () => {
    // 1000→600은 400명이 빠지지만 60%, 600→100은 500명이 빠지고 17%, 100→10은 90명이 빠지지만 10%
    const view = toFunnelView([
      step('view_item', 1000),
      step('apply_start', 600),
      step('begin_checkout', 100),
      step('purchase', 10),
    ])

    expect(view.map((s) => s.biggestDrop)).toEqual([false, false, false, true])
  })

  it('기록이 없으면 비율·막대·강조가 없다', () => {
    const view = toFunnelView([step('view_item', 0), step('begin_checkout', 0)])

    expect(view.map((s) => s.rate)).toEqual([undefined, undefined])
    expect(view.map((s) => s.barRatio)).toEqual([0, 0])
    expect(view.some((s) => s.biggestDrop)).toBe(false)
  })
  it('새 흐름의 시작 단계는 앞 단계와 비교하지 않고 가장 많이 빠지는 단계에서도 뺀다', () => {
    // 판매자 신청을 마친 사람과 대회 등록을 시작한 사람은 다른 사람이라 비교하지 않는다
    const view = toFunnelView([
      step('seller_apply_start', 0),
      step('seller_apply_submit', 0),
      { ...step('seller_race_form_start', 5), start: true },
      step('seller_race_register', 4),
    ])

    expect(view.map((s) => s.rate)).toEqual([undefined, undefined, undefined, 0.8])
    expect(view.map((s) => s.biggestDrop)).toEqual([false, false, false, true])
  })

  it('구매 단계는 실제로 보내는 이벤트만, 판매자 단계는 대회 등록 시작을 새 흐름으로 센다', () => {
    expect(FUNNELS.buyer.steps.map((s) => s.event)).not.toContain('add_to_cart')
    expect(FUNNELS.seller.steps.filter((s) => s.start).map((s) => s.event)).toEqual([
      'seller_race_form_start',
    ])
  })
})
