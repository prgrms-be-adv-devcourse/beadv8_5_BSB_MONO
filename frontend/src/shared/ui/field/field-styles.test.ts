import { describe, expect, it } from 'vitest'

import { FIELD_BOX } from './field-styles'

// 회원 시안(v4/Field): 높이 52, 둥글기 12, 그림자 면. 판매자센터는 globals.css의 --field-* 값으로 48·8·선을 쓴다.
describe('FIELD_BOX', () => {
  it('높이·둥글기·그림자·선을 --field-* 값으로 받는다', () => {
    expect(FIELD_BOX).toMatch(/\bmin-h-\(--field-min-h\)/)
    expect(FIELD_BOX).toMatch(/\brounded-\(--field-radius\)/)
    expect(FIELD_BOX).toMatch(/\bshadow-\(--field-shadow\)/)
    expect(FIELD_BOX).toMatch(/\binset-ring-\(--field-ring\)/)
  })
})
