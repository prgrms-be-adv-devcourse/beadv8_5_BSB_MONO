import { describe, expect, it } from 'vitest'

import { FIELD_BOX } from './field-styles'

// 판매자 시안(v4/Field): 입력칸 높이 48, 둥글기 8, 선 border/default
describe('FIELD_BOX', () => {
  it('높이 48, 둥글기 8을 쓴다', () => {
    expect(FIELD_BOX).toMatch(/\bmin-h-12\b/)
    expect(FIELD_BOX).toMatch(/\brounded-field\b/)
    expect(FIELD_BOX).not.toMatch(/\bmin-h-13\b|\brounded-md\b/)
  })
})
