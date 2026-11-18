import { describe, expect, it } from 'vitest'

import { searchTermSchema } from './search-term'

describe('searchTermSchema', () => {
  it('앞뒤 공백을 빼고, 빈 검색어는 통과시킨다', () => {
    expect(searchTermSchema.parse('  제주 트레일 ')).toBe('제주 트레일')
    expect(searchTermSchema.parse('  ')).toBe('')
  })
})
