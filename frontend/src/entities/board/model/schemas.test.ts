import { describe, expect, it } from 'vitest'

import { COMMENT_MAX, CONTENT_MAX, TITLE_MAX } from '../api/types'
import { commentSchema, postSearchSchema, postWriteSchema } from './schemas'

const post = { category: 'FREE', title: '제목', content: '본문' }

describe('postWriteSchema', () => {
  it('앞뒤 공백을 빼고, 공백만 쓴 제목·본문은 거절한다', () => {
    expect(postWriteSchema.parse({ ...post, title: ' 제목 ' }).title).toBe('제목')
    expect(postWriteSchema.safeParse({ ...post, title: '  ' }).success).toBe(false)
    expect(postWriteSchema.safeParse({ ...post, content: '' }).success).toBe(false)
  })

  it('제목·본문 최대 길이를 넘으면 거절한다', () => {
    expect(postWriteSchema.safeParse({ ...post, title: 'a'.repeat(TITLE_MAX + 1) }).success).toBe(
      false,
    )
    expect(
      postWriteSchema.safeParse({ ...post, content: 'a'.repeat(CONTENT_MAX + 1) }).success,
    ).toBe(false)
  })

  it('없는 말머리는 거절한다', () => {
    expect(postWriteSchema.safeParse({ ...post, category: 'ETC' }).success).toBe(false)
  })
})

describe('commentSchema', () => {
  it('공백만 쓰거나 최대 길이를 넘으면 거절한다', () => {
    expect(commentSchema.safeParse('   ').success).toBe(false)
    expect(commentSchema.safeParse('a'.repeat(COMMENT_MAX + 1)).success).toBe(false)
    expect(commentSchema.parse(' 응원해요 ')).toBe('응원해요')
  })
})

describe('postSearchSchema', () => {
  it('앞뒤 공백을 빼고, 빈 검색어는 통과시킨다', () => {
    expect(postSearchSchema.parse('  풀코스 ')).toBe('풀코스')
    expect(postSearchSchema.parse('   ')).toBe('')
  })
})
