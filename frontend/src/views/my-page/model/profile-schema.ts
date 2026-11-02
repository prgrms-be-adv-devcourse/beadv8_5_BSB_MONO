import { z } from 'zod'

import {
  BIRTH_UNDER_14_MESSAGE,
  isJoinableAge,
  isNicknameWidthValid,
  parseBirth,
} from '@/entities/member'
import { ApiError, memberZod } from '@/shared/api'

// 내 정보 수정 입력 검사. 닉네임 규칙은 orval이 만든 memberZod.ModifyMeBody(백엔드 MemberModifyRequestDto)를 따르고,
// 문구만 화면 말투로 바꾼다. 닉네임 칸 수는 명세에 없는 백엔드 커스텀 검증(@NicknameWidth)이라 entities/member 규칙을 쓴다.
// 가입 뒤에는 닉네임만 바꿀 수 있다(BUK-33). 이름 · 생년월일 · 휴대폰 번호는 서버가 받지 않아 형식만 본다.

export const PROFILE_ERRORS = {
  name: '이름을 입력해 주세요.',
  birth: '생년월일을 골라 주세요.',
  birthUnder14: BIRTH_UNDER_14_MESSAGE,
  phone: '휴대폰 번호를 010-1234-5678처럼 입력해 주세요.',
  nicknameChars: '닉네임은 한글 · 영문 · 숫자만 쓸 수 있어요.',
  nicknameWidth: '닉네임은 한글 2~8자, 영문 4~16자로 정해 주세요.',
  saveFailed: '지금은 저장할 수 없어요. 잠시 후 다시 시도해 주세요.',
} as const

const body = memberZod.ModifyMeBody.shape

/** 생성된 스키마로 검사하고, 틀리면 화면 문구를 띄운다. */
const follow = (schema: z.ZodType, message: string) =>
  z
    .string()
    .trim()
    .refine((value) => schema.safeParse(value).success, message)

export const profileSchema = z.object({
  name: z.string().trim().min(1, PROFILE_ERRORS.name),
  birth: z
    .string()
    .trim()
    .refine((value) => parseBirth(value) !== null, PROFILE_ERRORS.birth)
    .refine((value) => {
      const date = parseBirth(value)
      return date === null || isJoinableAge(date)
    }, PROFILE_ERRORS.birthUnder14),
  phone: z
    .string()
    .trim()
    .regex(/^01[016789]-?\d{3,4}-?\d{4}$/, PROFILE_ERRORS.phone),
  nickname: follow(body.nickname, PROFILE_ERRORS.nicknameChars).refine(
    isNicknameWidthValid,
    PROFILE_ERRORS.nicknameWidth,
  ),
})
export type ProfileValues = z.infer<typeof profileSchema>

/** 수정 실패를 어느 칸에 보여 줄지. form은 칸이 아닌 화면 아래 문구 */
export function modifyErrorMessage(error: unknown): {
  target: keyof ProfileValues | 'form'
  message: string
} {
  if (!(error instanceof ApiError)) return { target: 'form', message: PROFILE_ERRORS.saveFailed }
  const message = error.serverMessage ?? PROFILE_ERRORS.saveFailed
  switch (error.code) {
    case 'MEMBER_DUPLICATE_NICKNAME':
      return { target: 'nickname', message }
    case 'MEMBER_DUPLICATE_PHONE':
      return { target: 'phone', message }
    case 'MEMBER_UNDER_AGE':
      return { target: 'birth', message }
    default:
      return { target: 'form', message }
  }
}
