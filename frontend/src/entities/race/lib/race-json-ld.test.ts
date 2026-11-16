import { describe, expect, it } from 'vitest'

import { RACE_DETAIL } from '../model/fixtures'
import type { RaceDetail } from '../model/types'
import { raceJsonLd, serializeJsonLd } from './race-json-ld'

const race: RaceDetail = {
  ...RACE_DETAIL,
  raceDate: '2026-11-01T08:00:00',
  images: ['https://cdn.example/a.jpg'],
  place: '서울숲 일대',
  seller: { ...RACE_DETAIL.seller, company: '(주)쿠키런' },
}

describe('raceJsonLd', () => {
  it('이름 · 시작일 · 장소 · 이미지 · 주최를 schema.org Event로 만든다', () => {
    expect(raceJsonLd(race)).toEqual({
      '@context': 'https://schema.org',
      '@type': 'Event',
      name: race.title,
      startDate: '2026-11-01T08:00:00+09:00',
      location: { '@type': 'Place', name: '서울숲 일대' },
      image: ['https://cdn.example/a.jpg'],
      organizer: { '@type': 'Organization', name: '(주)쿠키런' },
    })
  })

  it('값이 없는 키는 뺀다', () => {
    const json = raceJsonLd({
      ...race,
      raceDate: undefined,
      images: [],
      place: '',
      seller: { ...race.seller, company: '', name: '' },
    })
    expect(Object.keys(json).sort()).toEqual(['@context', '@type', 'name'])
  })
})

describe('serializeJsonLd', () => {
  it('<를 \\u003c로 바꿔 </script>로 태그를 닫지 못하게 한다', () => {
    const text = serializeJsonLd({ name: '</script><script>alert(1)</script>' })
    expect(text).not.toContain('<')
    expect(JSON.parse(text)).toEqual({ name: '</script><script>alert(1)</script>' })
  })
})
