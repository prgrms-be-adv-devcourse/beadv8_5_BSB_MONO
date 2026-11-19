import { describe, expect, it } from 'vitest'

import { QUESTION_MAX, questionSchema } from './question-schema'

describe('questionSchema', () => {
  it('앞뒤 공백을 빼고, 공백만 쓰거나 제한을 넘으면 거절한다', () => {
    expect(questionSchema.parse('  키트는 몇 개예요? ')).toBe('키트는 몇 개예요?')
    expect(questionSchema.safeParse('   ').success).toBe(false)
    expect(questionSchema.safeParse('a'.repeat(QUESTION_MAX)).success).toBe(true)
    expect(questionSchema.safeParse('a'.repeat(QUESTION_MAX + 1)).success).toBe(false)
  })
})
