import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { siteArea, trackEvent, trackingGtmId } from './track'

const env = vi.hoisted(() => ({
  NEXT_PUBLIC_GTM_ID: 'GTM-TEST' as string | undefined,
  NEXT_PUBLIC_API_MOCKING: 'off' as 'on' | 'off',
}))
vi.mock('@/shared/config', () => ({ clientEnv: env }))

function visit(path: string) {
  window.history.replaceState(null, '', path)
}

beforeEach(() => {
  env.NEXT_PUBLIC_GTM_ID = 'GTM-TEST'
  env.NEXT_PUBLIC_API_MOCKING = 'off'
  window.dataLayer = []
  visit('/')
})

afterEach(() => {
  delete window.dataLayer
})

describe('trackingGtmId', () => {
  it('GTM ID가 없거나 가짜 응답 모드면 GTM을 넣지 않는다', () => {
    expect(trackingGtmId()).toBe('GTM-TEST')
    env.NEXT_PUBLIC_API_MOCKING = 'on'
    expect(trackingGtmId()).toBeUndefined()
    env.NEXT_PUBLIC_API_MOCKING = 'off'
    env.NEXT_PUBLIC_GTM_ID = undefined
    expect(trackingGtmId()).toBeUndefined()
  })
})

describe('siteArea', () => {
  it('/seller로 시작하면 판매자, 그 밖은 구매자', () => {
    expect(siteArea('/seller')).toBe('seller')
    expect(siteArea('/seller/races/new')).toBe('seller')
    expect(siteArea('/seller-apply')).toBe('seller')
    expect(siteArea('/races/1')).toBe('shop')
  })
})

describe('trackEvent', () => {
  it('이벤트 이름 · 구분 · 조회 값을 뺀 화면 주소 · 값을 dataLayer에 넣는다', () => {
    visit('/races/7/queue?option=3')
    trackEvent('queue_pass', { raceId: '7', waitSeconds: 42 })

    expect(window.dataLayer).toEqual([
      {
        event: 'queue_pass',
        site_area: 'shop',
        page_location: `${window.location.origin}/races/7/queue`,
        race_id: '7',
        wait_seconds: 42,
      },
    ])
  })

  it('충전 결과 화면의 토스 결제 값(조회 값)을 보내지 않는다', () => {
    visit('/me/deposit/charge/return?paymentKey=secret&orderId=charge-1&amount=10000')
    trackEvent('deposit_charge_done', { amount: 10_000, duringCheckout: true })

    expect(JSON.stringify(window.dataLayer)).not.toContain('secret')
    expect(window.dataLayer?.[0]).toMatchObject({
      event: 'deposit_charge_done',
      page_location: `${window.location.origin}/me/deposit/charge/return`,
      amount: 10_000,
      during_checkout: true,
    })
  })

  it('구매 이벤트는 앞 ecommerce 값을 비운 뒤 GA4 구매 형식으로 보낸다', () => {
    visit('/checkout/complete?orderId=55')
    trackEvent('purchase', {
      orderId: '55',
      value: 50_000,
      items: [{ raceId: '7', courseId: '3', price: 50_000 }],
    })

    expect(window.dataLayer).toEqual([
      { ecommerce: null },
      {
        event: 'purchase',
        site_area: 'shop',
        page_location: `${window.location.origin}/checkout/complete`,
        order_id: '55',
        item_count: 1,
        value: 50_000,
        ecommerce: {
          transaction_id: '55',
          currency: 'KRW',
          value: 50_000,
          items: [{ item_id: '7', item_variant: '3', price: 50_000, quantity: 1 }],
        },
      },
    ])
  })

  it('판매자 센터 이벤트는 판매자로 구분하고, 오류 묶음은 이름 목록과 개수로 보낸다', () => {
    visit('/seller/races/new?from=home')
    trackEvent('seller_race_form_error', { sections: ['기본 정보', '이미지'] })

    expect(window.dataLayer?.[0]).toMatchObject({
      event: 'seller_race_form_error',
      site_area: 'seller',
      error_sections: '기본 정보,이미지',
      error_count: 2,
    })
  })

  it('승인일을 모르면 승인 뒤 지난 날수를 빼고 보낸다', () => {
    visit('/seller/races/new')
    trackEvent('seller_race_register', { raceId: '9', courseCount: 2, isFirstRace: true })

    expect(window.dataLayer?.[0]).not.toHaveProperty('days_since_approval')
    expect(window.dataLayer?.[0]).toMatchObject({
      race_id: '9',
      course_count: 2,
      is_first_race: true,
    })
  })

  it('승인일을 읽지 못해 날수가 NaN이면 빼고 보낸다', () => {
    visit('/seller/races/new')
    trackEvent('seller_race_register', { raceId: '9', courseCount: 1, daysSinceApproval: NaN })
    expect(window.dataLayer?.[0]).not.toHaveProperty('days_since_approval')
  })

  it('dataLayer가 배열이 아니어도 예외를 던지지 않는다', () => {
    window.dataLayer = 'broken' as unknown as []
    expect(() => trackEvent('queue_enter', { raceId: '7' })).not.toThrow()
  })

  it('어드민 화면에서는 아무것도 보내지 않는다', () => {
    visit('/admin/sellers')
    trackEvent('view_item_list', { hasKeyword: false, resultCount: 3 })
    expect(window.dataLayer).toEqual([])
  })

  it('GTM ID가 없으면 아무것도 보내지 않는다', () => {
    env.NEXT_PUBLIC_GTM_ID = undefined
    trackEvent('view_item_list', { hasKeyword: true, resultCount: 0 })
    expect(window.dataLayer).toEqual([])
  })
})
