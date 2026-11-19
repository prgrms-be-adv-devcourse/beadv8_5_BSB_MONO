import { describe, expect, it } from 'vitest'

import { mockPassFormSchema } from './mock-pass-form'

const ok = { carrier: 'SKT', name: '김러너', birthDate: '1995-03-02', phone: '010-1234-5678' }

describe('mockPassFormSchema', () => {
  it('이름·휴대폰 번호의 앞뒤 공백을 뺀다', () => {
    expect(mockPassFormSchema.parse({ ...ok, name: ' 김러너 ', phone: ' 01012345678 ' })).toEqual({
      ...ok,
      phone: '01012345678',
    })
  })

  it('이름이 비었거나 생년월일·휴대폰 번호 형식이 틀리면 거절한다', () => {
    expect(mockPassFormSchema.safeParse({ ...ok, name: '  ' }).success).toBe(false)
    expect(mockPassFormSchema.safeParse({ ...ok, birthDate: '' }).success).toBe(false)
    expect(mockPassFormSchema.safeParse({ ...ok, phone: '02-123-4567' }).success).toBe(false)
  })

  it('없는 통신사는 거절한다', () => {
    expect(mockPassFormSchema.safeParse({ ...ok, carrier: 'ETC' }).success).toBe(false)
  })
})
