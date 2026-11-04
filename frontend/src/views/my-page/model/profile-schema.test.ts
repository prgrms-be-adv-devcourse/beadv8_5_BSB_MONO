import { describe, expect, it } from 'vitest'

import { PROFILE_ERRORS, profileSchema } from './profile-schema'

function nicknameError(nickname: string) {
  const result = profileSchema.safeParse({ nickname })
  return result.success ? null : (result.error.issues[0]?.message ?? null)
}

describe('profileSchema 닉네임', () => {
  it('정상 값은 통과한다', () => {
    expect(nicknameError('러너1')).toBeNull()
  })

  it('한글 · 영문 · 숫자 말고 다른 글자가 있으면 거절한다', () => {
    expect(nicknameError('러너!!')).toBe(PROFILE_ERRORS.nicknameChars)
  })

  it('칸 수가 백엔드 규칙(한글 2~8자, 영문 4~16자)에 안 맞으면 거절한다', () => {
    expect(nicknameError('abc')).toBe(PROFILE_ERRORS.nicknameWidth)
  })
})
