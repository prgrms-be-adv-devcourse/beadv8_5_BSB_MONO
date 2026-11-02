import { describe, expect, it } from 'vitest'

import { readGa4Config } from './config'
import { parseRealtime, parseUsersByEvent, usersByEventRequest } from './report'

// 테스트용 가짜 키. 실제 키가 아니다
const KEY = { client_email: 'stats@example.iam.gserviceaccount.com', private_key: 'not-a-real-key' }

describe('readGa4Config', () => {
  it('둘 중 하나라도 비면 연결 정보 없음', () => {
    expect(readGa4Config({ GA4_PROPERTY_ID: '123' })).toEqual({ status: 'missing' })
    expect(readGa4Config({ GA4_SERVICE_ACCOUNT_KEY: JSON.stringify(KEY) })).toEqual({
      status: 'missing',
    })
  })

  it('키를 JSON 한 줄과 base64 한 줄 모두 받는다', () => {
    const json = JSON.stringify(KEY)
    const base64 = Buffer.from(json).toString('base64')
    for (const raw of [json, base64]) {
      expect(readGa4Config({ GA4_PROPERTY_ID: '123', GA4_SERVICE_ACCOUNT_KEY: raw })).toEqual({
        status: 'ready',
        config: { propertyId: '123', credentials: KEY },
      })
    }
  })

  it('측정 ID나 깨진 키는 형식 오류이고, 오류 문구에 키 값을 넣지 않는다', () => {
    expect(
      readGa4Config({ GA4_PROPERTY_ID: 'G-Q0YTWRFVHV', GA4_SERVICE_ACCOUNT_KEY: '{}' }).status,
    ).toBe('invalid')
    const broken = readGa4Config({ GA4_PROPERTY_ID: '123', GA4_SERVICE_ACCOUNT_KEY: 'secret-xyz' })
    expect(broken.status).toBe('invalid')
    expect(JSON.stringify(broken)).not.toContain('secret-xyz')
  })
})

describe('GA4 보고서 요청과 응답', () => {
  const input = {
    events: ['view_item', 'purchase'],
    siteArea: 'shop',
    dateRanges: [
      { name: 'current', startDate: '2026-10-26', endDate: '2026-11-01' },
      { name: 'previous', startDate: '2026-10-19', endDate: '2026-10-25' },
    ],
  }

  it('사람 수(totalUsers)만 묻고 site_area로 거른다', () => {
    const request = usersByEventRequest('properties/123', input)

    expect(request.metrics).toEqual([{ name: 'totalUsers' }])
    expect(request.dimensions).toEqual([{ name: 'eventName' }])
    expect(request.dimensionFilter.andGroup.expressions[1].filter).toEqual({
      fieldName: 'customEvent:site_area',
      stringFilter: { matchType: 'EXACT', value: 'shop' },
    })
  })

  it('기간 이름별로 나누고, 행이 없는 이벤트는 0명으로 채운다', () => {
    const rows = [
      {
        dimensionValues: [{ value: 'view_item' }, { value: 'current' }],
        metricValues: [{ value: '120' }],
      },
      {
        dimensionValues: [{ value: 'view_item' }, { value: 'previous' }],
        metricValues: [{ value: '90' }],
      },
      {
        dimensionValues: [{ value: 'purchase' }, { value: 'current' }],
        metricValues: [{ value: '7' }],
      },
    ]

    expect(parseUsersByEvent(rows, input)).toEqual({
      current: { view_item: 120, purchase: 7 },
      previous: { view_item: 90, purchase: 0 },
    })
  })

  it('실시간 응답을 접속자 수와 많이 본 화면으로 바꾼다', () => {
    expect(
      parseRealtime(
        [{ metricValues: [{ value: '23' }] }],
        [{ dimensionValues: [{ value: '대회 목록' }], metricValues: [{ value: '9' }] }],
      ),
    ).toEqual({ activeUsers: 23, topScreens: [{ name: '대회 목록', activeUsers: 9 }] })
    expect(parseRealtime(undefined, undefined)).toEqual({ activeUsers: 0, topScreens: [] })
  })
})
