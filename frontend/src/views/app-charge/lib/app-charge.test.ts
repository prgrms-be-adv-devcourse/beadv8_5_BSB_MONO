import { describe, expect, it } from 'vitest'

import { appDoneUrl, appFailUrl, failPath, parseChargeQuery } from './app-charge'

const valid = {
  orderId: 'charge-abc123',
  amount: '12000',
  orderName: '크루런 예치금 충전',
  customerKey: 'cust-1',
}

describe('parseChargeQuery', () => {
  it('네 값이 모두 있으면 금액을 숫자로 바꿔 돌려준다', () => {
    expect(parseChargeQuery(valid)).toEqual({ ...valid, amount: 12_000 })
  })

  it.each(['orderId', 'amount', 'orderName', 'customerKey'])('%s가 빠지면 undefined', (key) => {
    expect(parseChargeQuery({ ...valid, [key]: undefined })).toBeUndefined()
    expect(parseChargeQuery({ ...valid, [key]: '' })).toBeUndefined()
  })

  it.each(['abc', '0', '-100', '1.5', '1e3', '12000원', ' ', '99999999999999999999'])(
    '금액이 %j이면 undefined',
    (amount) => {
      expect(parseChargeQuery({ ...valid, amount })).toBeUndefined()
    },
  )

  it('같은 이름이 여러 번 오면 믿지 않고 undefined', () => {
    expect(parseChargeQuery({ ...valid, orderId: ['a', 'b'] })).toBeUndefined()
  })
})

describe('appDoneUrl', () => {
  it('앱 주소에 paymentKey · orderId · amount를 붙인다', () => {
    const url = new URL(
      appDoneUrl({ paymentKey: 'pk_1', orderId: 'charge-abc123', amount: '12000' }),
    )
    expect(url.protocol + '//' + url.host + url.pathname).toBe('crewrun://deposit/charge-return')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      paymentKey: 'pk_1',
      orderId: 'charge-abc123',
      amount: '12000',
    })
  })

  it('값이 빠졌거나 금액이 숫자가 아니면 성공으로 넘기지 않고 실패 주소로 만든다', () => {
    for (const bad of [
      { orderId: 'o', amount: '1000' },
      { paymentKey: 'pk', amount: '1000' },
      { paymentKey: 'pk', orderId: 'o' },
      { paymentKey: 'pk', orderId: 'o', amount: 'abc' },
    ]) {
      const url = new URL(appDoneUrl(bad))
      expect(url.searchParams.get('code')).toBe('INVALID_RETURN')
      expect(url.searchParams.has('paymentKey')).toBe(false)
    }
  })

  it('쿼리로 돌아갈 주소를 넘겨도 항상 고정된 앱 주소로만 보낸다', () => {
    const url = appDoneUrl({
      paymentKey: 'pk',
      orderId: 'o',
      amount: '1000',
      returnUrl: 'https://evil.test',
      redirect: 'https://evil.test',
    })
    expect(url.startsWith('crewrun://deposit/charge-return?')).toBe(true)
    expect(url).not.toContain('evil.test')
  })
})

describe('appFailUrl', () => {
  it('앱 주소에 code · message · orderId를 붙인다', () => {
    const url = new URL(
      appFailUrl({ code: 'PAY_PROCESS_CANCELED', message: '취소됨', orderId: 'o1' }),
    )
    expect(url.protocol + '//' + url.host + url.pathname).toBe('crewrun://deposit/charge-return')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      code: 'PAY_PROCESS_CANCELED',
      message: '취소됨',
      orderId: 'o1',
    })
  })

  it('code가 없어도 앱이 실패로 알아볼 수 있게 기본 코드를 넣는다', () => {
    const url = new URL(appFailUrl({}))
    expect(url.searchParams.get('code')).toBe('UNKNOWN')
    expect(url.searchParams.has('orderId')).toBe(false)
  })
})

describe('failPath', () => {
  it('이 사이트의 실패 페이지 주소로 만든다', () => {
    const url = new URL(
      failPath('INVALID_REQUEST', '요청이 올바르지 않아요', 'o1'),
      'https://x.test',
    )
    expect(url.pathname).toBe('/app-charge/fail')
    expect(url.searchParams.get('code')).toBe('INVALID_REQUEST')
    expect(url.searchParams.get('orderId')).toBe('o1')
  })
})
