import { describe, expect, it } from 'vitest'

import { errorSections } from './use-race-form-tracking'

describe('errorSections', () => {
  it('오류 칸을 화면의 항목 묶음 이름으로 바꾸고, 화면 순서대로 겹치지 않게 돌려준다', () => {
    const errors = {
      refundDeadline: { type: 'custom', message: '' },
      title: { type: 'too_small', message: '' },
      location: { type: 'too_small', message: '' },
    }
    expect(errorSections(errors, true)).toEqual(['기본 정보', '이미지', '취소 · 환불 정책'])
  })

  it('오류가 없으면 빈 목록', () => {
    expect(errorSections({}, false)).toEqual([])
  })
})
