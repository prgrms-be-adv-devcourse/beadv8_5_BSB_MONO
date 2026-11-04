import { z } from 'zod'

import { isNicknameWidthValid } from '@/entities/member'
import { ApiError } from '@/shared/api'
import { memberZod } from '@/shared/api-zod'

// 내 정보 수정 입력 검사. 이름 · 생년월일 · 휴대폰은 본인인증으로 확인한 값이라 고치지 않고 닉네임만 검사한다.
// 규칙은 orval이 만든 memberZod.ModifyMeBody(백엔드 MemberModifyRequestDto)를 따르고, 문구만 화면 말투로 바꾼다.
// 닉네임 칸 수는 명세에 없는 백엔드 커스텀 검증(@NicknameWidth)이라 entities/member 규칙을 쓴다.

export const PROFILE_ERRORS = {
  nicknameChars: '닉네임은 한글 · 영문 · 숫자만 쓸 수 있어요.',
  nicknameWidth: '닉네임은 한글 2~8자, 영문 4~16자로 정해 주세요.',
  saveFailed: '지금은 저장할 수 없어요. 잠시 후 다시 시도해 주세요.',
} as const

export const profileSchema = z.object({
  nickname: z
    .string()
    .trim()
    .refine(
      (value) => memberZod.ModifyMeBody.shape.nickname.safeParse(value).success,
      PROFILE_ERRORS.nicknameChars,
    )
    .refine(isNicknameWidthValid, PROFILE_ERRORS.nicknameWidth),
})
export type ProfileValues = z.infer<typeof profileSchema>

/** 수정 실패를 어느 칸에 보여 줄지. form은 칸이 아닌 화면 아래 문구 */
export function modifyErrorMessage(error: unknown): {
  target: keyof ProfileValues | 'form'
  message: string
} {
  if (!(error instanceof ApiError)) return { target: 'form', message: PROFILE_ERRORS.saveFailed }
  const message = error.serverMessage ?? PROFILE_ERRORS.saveFailed
  return error.code === 'MEMBER_DUPLICATE_NICKNAME'
    ? { target: 'nickname', message }
    : { target: 'form', message }
}
