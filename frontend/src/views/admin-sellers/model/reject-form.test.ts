import { describe, expect, it } from 'vitest'

import { ADMIN_SELLER_ERRORS } from './admin-sellers-api'
import { rejectFormSchema } from './reject-form'

describe('rejectFormSchema', () => {
  it('앞뒤 공백을 빼고, 공백만 쓴 사유는 사유를 입력하라고 거절한다', () => {
    expect(rejectFormSchema.parse({ reason: ' 서류 누락 ' }).reason).toBe('서류 누락')
    const empty = rejectFormSchema.safeParse({ reason: '   ' })
    expect(empty.error?.issues[0].message).toBe(ADMIN_SELLER_ERRORS.reasonRequired)
  })

  it('500자를 넘으면 거절한다', () => {
    expect(rejectFormSchema.safeParse({ reason: 'a'.repeat(500) }).success).toBe(true)
    expect(rejectFormSchema.safeParse({ reason: 'a'.repeat(501) }).success).toBe(false)
  })
})
