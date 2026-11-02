import { describe, expect, it } from 'vitest'

import {
  AUTH_ERRORS,
  findEmailSchema,
  loginSchema,
  passwordNewSchema,
  signupAccountSchema,
  signupProfileSchema,
} from './schemas'

// 실패한 첫 오류 문구를 필드 이름과 함께 돌려준다.
function firstError(result: {
  success: boolean
  error?: { issues: { path: PropertyKey[]; message: string }[] }
}) {
  const issue = result.error?.issues[0]
  return issue && { path: issue.path.join('.'), message: issue.message }
}

describe('loginSchema', () => {
  it('이메일 형식이 틀리면 형식 오류를 낸다', () => {
    const result = loginSchema.safeParse({ email: 'runner', password: 'x' })
    expect(firstError(result)).toEqual({ path: 'email', message: AUTH_ERRORS.emailFormat })
  })

  it('비밀번호가 비면 입력해 달라고 한다', () => {
    const result = loginSchema.safeParse({ email: 'runner@crewrun.kr', password: '' })
    expect(firstError(result)).toEqual({ path: 'password', message: AUTH_ERRORS.passwordRequired })
  })

  it('맞는 값은 통과한다', () => {
    expect(loginSchema.safeParse({ email: 'runner@crewrun.kr', password: 'a' }).success).toBe(true)
  })
})

describe('signupAccountSchema', () => {
  const valid = {
    email: 'runner@crewrun.kr',
    password: 'password12',
    passwordConfirm: 'password12',
  }

  it('맞는 값은 통과한다', () => {
    expect(signupAccountSchema.safeParse(valid).success).toBe(true)
  })

  it.each(['password1', '123456789', ' '.repeat(10)])(
    '비밀번호 규칙(10자 이상, 공백만은 안 됨)에 안 맞으면 막는다: "%s"',
    (password) => {
      const result = signupAccountSchema.safeParse({
        ...valid,
        password,
        passwordConfirm: password,
      })
      expect(firstError(result)).toEqual({ path: 'password', message: AUTH_ERRORS.passwordRule })
    },
  )

  it('10자면 영문 · 숫자 섞지 않아도 통과한다 (백엔드와 같음)', () => {
    const password = '1234567890'
    expect(
      signupAccountSchema.safeParse({ ...valid, password, passwordConfirm: password }).success,
    ).toBe(true)
  })

  it('비밀번호 확인이 다르면 확인 칸에 오류를 낸다', () => {
    const result = signupAccountSchema.safeParse({ ...valid, passwordConfirm: 'password2' })
    expect(firstError(result)).toEqual({
      path: 'passwordConfirm',
      message: AUTH_ERRORS.passwordMismatch,
    })
  })
})

describe('signupProfileSchema', () => {
  // 이름 · 생년월일 · 휴대폰 번호는 본인인증으로 받으므로 닉네임과 성별만 검사한다
  const validProfile = { nickname: '달리는곰', gender: '' as const }

  it('맞는 값은 통과하고, 성별은 고르지 않아도 된다', () => {
    expect(signupProfileSchema.safeParse(validProfile).success).toBe(true)
    expect(signupProfileSchema.safeParse({ ...validProfile, gender: 'FEMALE' }).success).toBe(true)
    expect(signupProfileSchema.safeParse({ ...validProfile, gender: 'OTHER' }).success).toBe(false)
  })

  it('닉네임 길이를 검사한다', () => {
    expect(firstError(signupProfileSchema.safeParse({ ...validProfile, nickname: '곰' }))).toEqual({
      path: 'nickname',
      message: AUTH_ERRORS.nicknameLength,
    })
  })

  it.each([
    ['러너', true],
    ['abcd', true],
    ['가나다라마바사아', true],
    ['abc', false],
    ['가나다라마바사아자', false],
  ])('닉네임은 한글 2~8자, 영문 4~16자만 허용한다: %s → %s', (nickname, valid) => {
    const result = signupProfileSchema.safeParse({ ...validProfile, nickname })
    expect(result.success).toBe(valid)
    if (!valid) expect(firstError(result)?.message).toBe(AUTH_ERRORS.nicknameLength)
  })

  it('닉네임에 한글 · 영문 · 숫자 말고 다른 글자가 있으면 막는다', () => {
    const result = signupProfileSchema.safeParse({ ...validProfile, nickname: '달리는 곰' })
    expect(firstError(result)).toEqual({ path: 'nickname', message: AUTH_ERRORS.nicknameChars })
  })
})

describe('찾기 · 재설정 스키마', () => {
  it('이메일 찾기는 나이 제한 없이 생년월일 형식만 본다', () => {
    const year = new Date().getFullYear() - 5
    const result = findEmailSchema.safeParse({
      name: '김러너',
      birth: `${year}-01-01`,
      phone: '01012345678',
    })
    expect(result.success).toBe(true)
  })

  it('새 비밀번호 확인이 다르면 막는다', () => {
    const result = passwordNewSchema.safeParse({ password: 'password12', passwordConfirm: 'x' })
    expect(firstError(result)?.path).toBe('passwordConfirm')
  })
})
