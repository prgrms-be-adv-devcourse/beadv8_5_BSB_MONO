import { describe, expect, it } from 'vitest'

import {
  AUTH_ERRORS,
  findEmailSchema,
  loginSchema,
  PASSWORD_MAX_BYTES,
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
  }

  it('맞는 값은 통과한다', () => {
    expect(signupAccountSchema.safeParse(valid).success).toBe(true)
  })

  it.each(['password1', '123456789', ' '.repeat(10)])(
    '비밀번호 규칙(10자 이상, 공백만은 안 됨)에 안 맞으면 막는다: "%s"',
    (password) => {
      const result = signupAccountSchema.safeParse({ ...valid, password })
      expect(firstError(result)).toEqual({ path: 'password', message: AUTH_ERRORS.passwordRule })
    },
  )

  it('10자면 영문 · 숫자 섞지 않아도 통과한다 (백엔드와 같음)', () => {
    const password = '1234567890'
    expect(signupAccountSchema.safeParse({ ...valid, password }).success).toBe(true)
  })

  // 서버는 글자 수가 아니라 UTF-8 72바이트로 막는다(BCrypt 한계). 한글은 한 글자가 3바이트다.
  it('한글 25자(75바이트)는 「너무 길어요」로 막고, 72바이트 이하는 통과한다', () => {
    expect(PASSWORD_MAX_BYTES).toBe(72)
    expect(
      firstError(signupAccountSchema.safeParse({ ...valid, password: '가'.repeat(25) })),
    ).toEqual({ path: 'password', message: AUTH_ERRORS.passwordTooLong })
    expect(signupAccountSchema.safeParse({ ...valid, password: '가'.repeat(24) }).success).toBe(
      true,
    )
    // 영문 · 숫자는 한 글자가 1바이트라 72자까지
    expect(signupAccountSchema.safeParse({ ...valid, password: 'a'.repeat(72) }).success).toBe(true)
    expect(
      firstError(signupAccountSchema.safeParse({ ...valid, password: 'a'.repeat(73) })),
    ).toEqual({ path: 'password', message: AUTH_ERRORS.passwordTooLong })
    // 글자 수는 64자 이하여도 바이트가 넘으면 막는다
    expect(
      signupAccountSchema.safeParse({ ...valid, password: 'a'.repeat(30) + '가'.repeat(15) })
        .success,
    ).toBe(false)
  })

  it('비밀번호 재설정도 같은 바이트 상한을 건다', () => {
    const password = '가'.repeat(25)
    expect(passwordNewSchema.safeParse({ password, passwordConfirm: password }).success).toBe(false)
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
  it('이메일 찾기는 나이 제한 없이 생년월일 숫자 8자리만 본다', () => {
    const year = new Date().getFullYear() - 5
    const find = (birth: string) =>
      findEmailSchema.safeParse({ name: '김러너', birth, phone: '01012345678' }).success
    expect(find(`${year}0101`)).toBe(true)
    expect(find(`${year}-01-01`)).toBe(false)
    expect(find('19951340')).toBe(false)
  })

  it('새 비밀번호 확인이 다르면 막는다', () => {
    const result = passwordNewSchema.safeParse({ password: 'password12', passwordConfirm: 'x' })
    expect(firstError(result)?.path).toBe('passwordConfirm')
  })
})
