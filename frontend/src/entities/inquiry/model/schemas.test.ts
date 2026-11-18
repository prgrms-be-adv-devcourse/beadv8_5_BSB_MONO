import { describe, expect, it } from 'vitest'

import { answerSchema, inquiryFormSchema } from './schemas'
import { ANSWER_MAX, BODY_MAX, TITLE_MAX } from './types'

describe('inquiryFormSchema', () => {
  it('앞뒤 공백을 빼고, 공백만 쓴 제목·내용은 거절한다', () => {
    expect(inquiryFormSchema.parse({ title: ' 제목 ', body: ' 내용 ' })).toEqual({
      title: '제목',
      body: '내용',
    })
    expect(inquiryFormSchema.safeParse({ title: '  ', body: '내용' }).success).toBe(false)
    expect(inquiryFormSchema.safeParse({ title: '제목', body: '' }).success).toBe(false)
  })

  it('서버 글자 수 제한을 넘으면 거절한다', () => {
    const ok = { title: 'a'.repeat(TITLE_MAX), body: 'a'.repeat(BODY_MAX) }
    expect(inquiryFormSchema.safeParse(ok).success).toBe(true)
    expect(inquiryFormSchema.safeParse({ ...ok, title: ok.title + 'a' }).success).toBe(false)
    expect(inquiryFormSchema.safeParse({ ...ok, body: ok.body + 'a' }).success).toBe(false)
  })
})

describe('answerSchema', () => {
  it('공백만 쓰거나 제한을 넘으면 거절하고, 앞뒤 공백은 뺀다', () => {
    expect(answerSchema.safeParse('   ').success).toBe(false)
    expect(answerSchema.safeParse('a'.repeat(ANSWER_MAX + 1)).success).toBe(false)
    expect(answerSchema.parse(' 답변 ')).toBe('답변')
  })
})
