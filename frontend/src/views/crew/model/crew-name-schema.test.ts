import { describe, expect, it } from 'vitest'

import { crewNameSchema } from './crew-name-schema'

describe('crewNameSchema', () => {
  it('앞뒤 공백을 빼고 1~20자를 받는다', () => {
    expect(crewNameSchema.parse({ name: '  성수런 ' })).toEqual({ name: '성수런' })
    expect(crewNameSchema.safeParse({ name: '가'.repeat(20) }).success).toBe(true)
    expect(crewNameSchema.safeParse({ name: '가'.repeat(21) }).success).toBe(false)
  })

  it('공백만 넣으면 이름을 입력하라고 한다', () => {
    const result = crewNameSchema.safeParse({ name: '   ' })
    expect(result.error?.issues[0]?.message).toBe('크루 이름을 입력해 주세요')
  })
})
