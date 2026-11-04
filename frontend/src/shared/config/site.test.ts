import { describe, expect, it } from 'vitest'

import { siteVerification } from './site'

describe('siteVerification', () => {
  it('값이 있는 검색엔진의 소유 확인 태그만 넣는다', () => {
    expect(siteVerification({ NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION: 'g-code' })).toEqual({
      google: 'g-code',
    })
    expect(siteVerification({ NEXT_PUBLIC_NAVER_SITE_VERIFICATION: 'n-code' })).toEqual({
      other: { 'naver-site-verification': 'n-code' },
    })
  })

  it('값이 없으면 확인 태그를 넣지 않는다', () => {
    expect(siteVerification({})).toBeUndefined()
  })
})
