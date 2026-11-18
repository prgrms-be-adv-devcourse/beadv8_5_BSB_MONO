import { describe, expect, it } from 'vitest'

import { claimSearchSchema } from './claim-search'

describe('claimSearchSchema', () => {
  it('앞뒤 공백을 빼고, 빈 값은 통과시킨다', () => {
    expect(claimSearchSchema.parse('  RC-20261215-0001 ')).toBe('RC-20261215-0001')
    expect(claimSearchSchema.parse('   ')).toBe('')
  })
})
