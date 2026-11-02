import {
  answerPassVerification,
  answerPhoneVerificationConfirm,
  answerPhoneVerificationRequest,
  ApiError,
  memberApi,
  unwrapData,
  type memberModel,
  type MockAnswer,
} from '@/shared/api'
import { clientEnv } from '@/shared/config'

/**
 * 휴대폰 인증 · PASS 본인인증 요청. 백엔드에는 아직 이 API가 없다(목업).
 * - 가짜 응답 모드(API_MOCKING=on): /api로 보내 MSW 핸들러가 답한다.
 * - 실제 백엔드 모드: 보내면 404가 나므로 브라우저 안에서 같은 규칙(shared/api/identity-verification-mock.ts)으로 답한다.
 * 어느 쪽이든 실패는 ApiError(code)로 던진다.
 */
type Mode = { mocking: boolean }

const browserMode = (): Mode => ({ mocking: clientEnv.NEXT_PUBLIC_API_MOCKING === 'on' })

/** 브라우저 안 가짜 응답을 진짜 응답처럼 바꾼다. 화면이 멈춘 것처럼 보이지 않게 조금 기다린다. */
async function fromLocal<T>(answer: MockAnswer<T>, url: string): Promise<T> {
  await new Promise((resolve) => setTimeout(resolve, 300))
  if (answer.status >= 300 || answer.body.data === null) {
    throw new ApiError(answer.status, answer.body, url)
  }
  return answer.body.data
}

export async function requestPhoneCode(
  phone: string,
  mode = browserMode(),
): Promise<memberModel.PhoneVerificationDto> {
  const url = memberApi.getRequestPhoneVerificationUrl()
  if (!mode.mocking) return fromLocal(answerPhoneVerificationRequest({ phone }), url)
  return unwrapData(await memberApi.requestPhoneVerification({ phone }))
}

export async function confirmPhoneCode(
  verificationId: string,
  code: string,
  mode = browserMode(),
): Promise<memberModel.IdentityVerificationDto> {
  const url = memberApi.getConfirmPhoneVerificationUrl(verificationId)
  if (!mode.mocking) return fromLocal(answerPhoneVerificationConfirm(verificationId, { code }), url)
  return unwrapData(await memberApi.confirmPhoneVerification(verificationId, { code }))
}

export async function verifyWithPass(
  body: memberModel.PassVerificationRequestDto,
  mode = browserMode(),
): Promise<memberModel.IdentityVerificationDto> {
  const url = memberApi.getVerifyIdentityWithPassUrl()
  if (!mode.mocking) return fromLocal(answerPassVerification(body), url)
  return unwrapData(await memberApi.verifyIdentityWithPass(body))
}
