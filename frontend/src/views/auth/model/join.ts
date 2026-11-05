import { ApiError, type memberModel } from '@/shared/api'

import {
  AUTH_ERRORS,
  signupAccountSchema,
  type SignupAccountValues,
  type SignupProfileValues,
} from './schemas'
import { agreedTermTypes, type SignupTermChecks } from './signup-terms'

/**
 * 가입 단계 입력을 가입 요청(POST /api/v1/auth/join) 본문으로 바꾼다.
 * 이름 · 생년월일 · 휴대폰 번호는 보내지 않는다. 서버가 본인인증 토큰으로 확인한 값을 쓴다.
 * 약관은 동의한 종류만 agreedTerms로 보낸다("만 14세 이상"은 약관이 아니라 보내지 않는다). 성별은 고르지 않으면 뺀다.
 */
export function toJoinRequest(
  account: SignupAccountValues,
  profile: SignupProfileValues,
  terms: SignupTermChecks,
  verificationToken: string,
): memberModel.MemberJoinRequestDto {
  const { email, password } = signupAccountSchema.parse(account)
  return {
    email,
    password,
    nickname: profile.nickname.trim(),
    gender: profile.gender || undefined,
    agreedTerms: agreedTermTypes(terms),
    verificationToken,
  }
}

/**
 * 가입 실패를 어디에 보여 줄지. nickname은 닉네임 칸, form은 칸이 아닌 화면 아래 문구.
 * verification은 본인인증을 처음부터 다시 해야 한다는 뜻이다(화면 아래 문구 + 인증 결과 지우기).
 * duplicate는 이미 가입한 사람이라는 뜻이다(모달로 로그인 안내, 시안 M04-P 3).
 * rejoinTooSoon은 탈퇴하고 30일이 지나지 않았다는 뜻이다(모달로 다시 가입할 수 있는 날 안내).
 */
export type JoinErrorTarget = 'nickname' | 'form' | 'verification' | 'duplicate' | 'rejoinTooSoon'

/**
 * 가입 실패 응답을 화면 문구로 바꾼다.
 * 이메일 중복은 계정 단계의 중복 확인 API가 먼저 막지만, 그 사이에 가입된 경우를 위해 409도 처리한다.
 * INVALID_REQUEST(칸이 없거나 모르는 값)와 INTERNAL_ERROR(시행 중인 약관이 없을 때 등)는
 * 사용자가 고칠 수 없으므로 서버 문구 대신 일반 안내를 보인다.
 */
export function joinErrorMessage(error: unknown): { target: JoinErrorTarget; message: string } {
  if (!(error instanceof ApiError)) return { target: 'form', message: AUTH_ERRORS.joinUnavailable }
  const message = error.serverMessage ?? AUTH_ERRORS.joinUnavailable
  switch (error.code) {
    case 'MEMBER_DUPLICATE_EMAIL':
      return { target: 'form', message: AUTH_ERRORS.emailTaken }
    case 'MEMBER_DUPLICATE_NICKNAME':
      return { target: 'nickname', message }
    // 휴대폰 번호는 본인인증으로 받으므로, 번호가 겹치면 같은 사람이 이미 가입한 것이다
    case 'MEMBER_DUPLICATE_PHONE':
    case 'MEMBER_DUPLICATE_IDENTITY':
      return { target: 'duplicate', message }
    case 'MEMBER_REQUIRED_TERMS_NOT_AGREED':
      return { target: 'form', message: AUTH_ERRORS.joinTermsRequired }
    case 'INVALID_REQUEST':
    case 'INTERNAL_ERROR':
      return { target: 'form', message: AUTH_ERRORS.joinUnavailable }
    case 'AUTH_VERIFICATION_REQUIRED':
      return { target: 'verification', message: AUTH_ERRORS.verificationExpired }
    case 'MEMBER_REJOIN_TOO_SOON':
      return { target: 'rejoinTooSoon', message }
    case 'MEMBER_UNDER_AGE':
      return { target: 'form', message: AUTH_ERRORS.birthUnder14 }
    default:
      return { target: 'form', message }
  }
}
