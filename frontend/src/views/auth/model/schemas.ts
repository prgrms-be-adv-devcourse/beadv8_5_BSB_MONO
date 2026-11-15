import { z } from 'zod'

import {
  BIRTH_UNDER_14_MESSAGE,
  isNicknameWidthValid,
  NICKNAME_PATTERN,
  parseBirth,
} from '@/entities/member'
import { memberZod } from '@/shared/api-zod'

// 인증 화면 입력 검사. 백엔드 API가 생기면 orval이 만든 zod 스키마로 바꾼다.
// 오류 문구는 피그마 시안(M03-E 로그인 실패, M04-1E 가입 입력 오류)과 helper 문구를 따른다.

export const AUTH_ERRORS = {
  emailRequired: '이메일을 입력해 주세요.',
  emailFormat: '이메일 형식이 맞지 않아요.',
  passwordRequired: '비밀번호를 입력해 주세요.',
  passwordRule: '비밀번호는 10자 이상으로 정해 주세요.',
  passwordMismatch: '비밀번호가 서로 달라요.',
  passwordTooLong: '비밀번호가 너무 길어요. 조금 줄여 주세요.',
  loginFailed: '이메일 또는 비밀번호가 맞지 않아요.',
  // 시안에 없는 문구. 서버에 닿지 못했거나 서버 문구가 없을 때
  loginUnavailable: '지금은 로그인할 수 없어요. 잠시 후 다시 시도해 주세요.',
  emailTaken: '이미 가입된 이메일이에요. 로그인하거나 비밀번호를 재설정해 주세요.',
  // 시안에 없는 문구. 가입 요청이 서버에 닿지 못했거나 서버 문구가 없을 때
  joinUnavailable: '지금은 가입할 수 없어요. 잠시 후 다시 시도해 주세요.',
  // 시안에 없는 문구. 이메일 중복 확인 요청이 실패했을 때
  emailCheckFailed: '이메일을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.',
  // 시안에 없는 문구. 가입 요청에서 필수 약관이 빠졌다는 서버 응답
  joinTermsRequired: '필수 약관에 동의해 주세요.',
  // 시안에 없는 문구. 본인인증 결과가 15분이 지나 서버에서 사라졌다는 응답
  verificationExpired: '본인인증한 지 15분이 지났어요. 다시 본인인증해 주세요.',
  nameRequired: '이름을 입력해 주세요.',
  birthFormat: '생년월일을 숫자 8자리로 입력해 주세요.',
  birthUnder14: BIRTH_UNDER_14_MESSAGE,
  phoneFormat: '휴대폰 번호를 010-1234-5678처럼 입력해 주세요.',
  nicknameChars: '닉네임은 한글 · 영문 · 숫자만 쓸 수 있어요.',
  nicknameLength: '닉네임은 한글 2~8자, 영문 4~16자로 정해 주세요.',
} as const

const email = z
  .string()
  .trim()
  .min(1, AUTH_ERRORS.emailRequired)
  .pipe(z.email(AUTH_ERRORS.emailFormat))

// 서버는 비밀번호를 UTF-8 72바이트까지만 받는다(BCrypt 한계, 백엔드 @PasswordBytes). 한글은 한 글자가 3바이트라 24자까지다.
export const PASSWORD_MAX_BYTES = 72
export const isPasswordTooLong = (value: string) =>
  new TextEncoder().encode(value).length > PASSWORD_MAX_BYTES

// 10자 이상, 공백만은 안 됨, 72바이트 이하(시안 「비밀번호 · 너무 김」). 백엔드 가입 요청 DTO(@NotBlank, @Size(min = 10), @PasswordBytes)와 같다.
const newPassword = z
  .string()
  .min(memberZod.joinBodyPasswordMin, AUTH_ERRORS.passwordRule)
  .refine((value) => value.trim().length > 0, AUTH_ERRORS.passwordRule)
  .refine((value) => !isPasswordTooLong(value), AUTH_ERRORS.passwordTooLong)

// 이메일 찾기의 생년월일은 숫자 8자리(19950302)로 받는다
const birth = z
  .string()
  .trim()
  .refine(
    (value) =>
      /^\d{8}$/.test(value) &&
      parseBirth(`${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6)}`) !== null,
    AUTH_ERRORS.birthFormat,
  )

const name = z.string().trim().min(1, AUTH_ERRORS.nameRequired)

// 백엔드 가입 요청 DTO의 닉네임 검사(@Pattern, @NicknameWidth)와 같다.
const nickname = z
  .string()
  .trim()
  .regex(NICKNAME_PATTERN, AUTH_ERRORS.nicknameChars)
  .refine(isNicknameWidthValid, AUTH_ERRORS.nicknameLength)

const phone = z
  .string()
  .trim()
  .regex(/^01[016789]-?\d{3,4}-?\d{4}$/, AUTH_ERRORS.phoneFormat)

// 비밀번호 확인 칸이 다르면 확인 칸 아래에 오류를 띄운다.
const confirmMatches = (v: { password: string; passwordConfirm: string }) =>
  v.password === v.passwordConfirm
const MISMATCH = { message: AUTH_ERRORS.passwordMismatch, path: ['passwordConfirm'] }

// 로그인은 비밀번호 규칙을 알려 주지 않는다. 틀리면 서버가 M03-E 문구로 답한다.
export const loginSchema = z.object({
  email,
  password: z.string().min(1, AUTH_ERRORS.passwordRequired),
})
export type LoginValues = z.infer<typeof loginSchema>

// 가입은 이메일 → 비밀번호 → 닉네임 순서로 한 칸씩 받는다. 비밀번호는 한 번만 적는다.
export const signupEmailSchema = z.object({ email })
export const signupPasswordSchema = z.object({
  password: newPassword,
})
export const signupAccountSchema = z.object({
  email,
  password: signupPasswordSchema.shape.password,
})
export type SignupAccountValues = z.infer<typeof signupAccountSchema>

// 이름 · 생년월일 · 휴대폰 번호는 본인인증으로 받으므로 회원 정보 단계에서는 닉네임과 성별(선택)만 적는다
export const signupProfileSchema = z.object({
  nickname,
  gender: z.enum(['', 'MALE', 'FEMALE']),
})
export type SignupProfileValues = z.infer<typeof signupProfileSchema>

export const findEmailSchema = z.object({ name, birth, phone })
export type FindEmailValues = z.infer<typeof findEmailSchema>

export const passwordResetSchema = z.object({ email })
export type PasswordResetValues = z.infer<typeof passwordResetSchema>

export const passwordNewSchema = z
  .object({ password: newPassword, passwordConfirm: z.string() })
  .refine(confirmMatches, MISMATCH)
export type PasswordNewValues = z.infer<typeof passwordNewSchema>
