import { requestIdentityVerification as openPortOneWindow } from '@portone/browser-sdk/v2'

import { ApiError, memberApi, unwrapData, type memberModel } from '@/shared/api'
import { clientEnv } from '@/shared/config'

/**
 * 가입 본인인증 (포트원 V2 + KG이니시스 통합인증, BUK-33).
 * 순서: 백엔드가 인증 ID를 만든다 → 포트원 창을 연다 → 백엔드가 포트원에서 결과를 직접 확인한다.
 * 가짜 응답 모드(API_MOCKING=on)는 포트원 창 대신 체험용 창(MockPassSheet)이 verifyWithMockPass를 부른다.
 */

export type VerifiedIdentity = memberModel.VerifiedIdentityDto

export type IdentityVerificationOutcome =
  | { status: 'verified'; identity: VerifiedIdentity }
  /** 모바일은 포트원 창이 페이지를 옮겨 간다. 결과는 돌아온 주소의 쿼리로 온다 (completeFromRedirect) */
  | { status: 'redirecting' }
  /** 사용자가 포트원 창을 직접 닫았다. 실패가 아니므로 안내 없이 원래 화면으로 돌아온다 */
  | { status: 'cancelled' }
  /** 이미 가입한 사람이다(CI 또는 이름 · 생년월일 · 휴대폰 번호가 같음). 화면은 로그인을 안내한다 */
  | { status: 'duplicate' }
  | { status: 'failed'; message: string }

export const IDENTITY_MESSAGES = {
  failed: '본인인증을 마치지 못했어요. 다시 시도해 주세요.',
  unavailable: '본인인증 결과를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.',
  notConfigured: '지금은 본인인증을 할 수 없어요. 잠시 후 다시 시도해 주세요.',
  popupBlocked: '브라우저가 본인인증 창을 막았어요. 이 사이트의 팝업을 허용한 뒤 다시 눌러 주세요.',
} as const

/** 가짜 응답 모드인지. 이때는 포트원 창 대신 체험용 창을 쓴다 */
export function isIdentityMock() {
  return clientEnv.NEXT_PUBLIC_API_MOCKING === 'on'
}

function failureMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return IDENTITY_MESSAGES.failed
  if (error.code === 'AUTH_VERIFICATION_UNAVAILABLE') return IDENTITY_MESSAGES.unavailable
  return IDENTITY_MESSAGES.failed
}

// 포트원 문서에 「사용자가 창을 닫음」 코드가 따로 없다. SDK 오류 코드(Cancelled)나 취소 문구로 판단한다
function isCancel(code: string | undefined, message: string | undefined) {
  return code === 'Cancelled' || (message ?? '').includes('취소')
}

async function complete(identityVerificationId: string): Promise<IdentityVerificationOutcome> {
  try {
    const identity = unwrapData(
      await memberApi.completeIdentityVerification(identityVerificationId),
    )
    return { status: 'verified', identity }
  } catch (error) {
    if (error instanceof ApiError && error.code === 'MEMBER_DUPLICATE_IDENTITY') {
      return { status: 'duplicate' }
    }
    return { status: 'failed', message: failureMessage(error) }
  }
}

/**
 * 백엔드에서 포트원 창을 열 인증 ID를 받는다. 받지 못하면 undefined.
 * 버튼을 누른 뒤에 받으면 창을 여는 시점이 늦어져 브라우저가 팝업으로 막으므로, 화면을 열 때 미리 받아 둔다.
 */
export async function prepareIdentityVerification(): Promise<string | undefined> {
  return memberApi
    .startIdentityVerification()
    .then((response) => unwrapData(response).identityVerificationId)
    .catch(() => undefined)
}

/**
 * 포트원 본인인증 창을 열고 결과를 받는다. 창을 여는 일과 결과를 받는 일은 모두 이 함수 하나에서 한다.
 * PC는 팝업 창이라, 버튼을 누른 직후에 열어야 막히지 않는다. 그래서 미리 받아 둔 인증 ID를 넘기면 기다리지 않고 바로 연다.
 * redirectUrl은 모바일에서 인증을 마친 뒤 돌아올 가입 화면 주소다. PC는 창이 닫히면 이 함수가 결과를 돌려준다.
 */
export async function requestIdentityVerification({
  redirectUrl,
  identityVerificationId: preparedId,
}: {
  redirectUrl: string
  identityVerificationId?: string
}): Promise<IdentityVerificationOutcome> {
  const storeId = clientEnv.NEXT_PUBLIC_PORTONE_STORE_ID
  const channelKey = clientEnv.NEXT_PUBLIC_PORTONE_IDENTITY_CHANNEL_KEY
  if (!storeId || !channelKey) return { status: 'failed', message: IDENTITY_MESSAGES.notConfigured }

  const identityVerificationId = preparedId ?? (await prepareIdentityVerification())
  if (!identityVerificationId) return { status: 'failed', message: IDENTITY_MESSAGES.notConfigured }

  let popupBlocked = false
  const response = await openPortOneWindow({
    storeId,
    channelKey,
    identityVerificationId,
    redirectUrl,
  }).catch((error: unknown) => {
    popupBlocked = error instanceof Error && error.message.includes('팝업')
    return null
  })
  // undefined는 페이지를 옮겨 가는 중이라는 뜻이다(리디렉션 방식)
  if (response === undefined) return { status: 'redirecting' }
  if (response === null) {
    return {
      status: 'failed',
      message: popupBlocked ? IDENTITY_MESSAGES.popupBlocked : IDENTITY_MESSAGES.failed,
    }
  }
  if (response.code) {
    return isCancel(response.code, response.message)
      ? { status: 'cancelled' }
      : { status: 'failed', message: IDENTITY_MESSAGES.failed }
  }
  return complete(identityVerificationId)
}

/**
 * 모바일 리디렉션으로 돌아온 주소의 쿼리에서 결과를 받는다. 본인인증으로 돌아온 주소가 아니면 null.
 * 포트원은 identityVerificationId와, 실패했으면 code를 붙여 돌려보낸다.
 */
export async function completeFromRedirect(
  search: URLSearchParams,
): Promise<IdentityVerificationOutcome | null> {
  const identityVerificationId = search.get('identityVerificationId')
  if (!identityVerificationId) return null
  const code = search.get('code')
  if (code) {
    return isCancel(code, search.get('message') ?? undefined)
      ? { status: 'cancelled' }
      : { status: 'failed', message: IDENTITY_MESSAGES.failed }
  }
  return complete(identityVerificationId)
}

/** 체험용 창의 인증 (가짜 응답 모드 전용). 입력한 값을 확인된 정보로 돌려받는다 */
export async function verifyWithMockPass(
  body: memberModel.PassVerificationRequestDto,
): Promise<IdentityVerificationOutcome> {
  try {
    const identity = unwrapData(await memberApi.verifyIdentityWithMockPass(body))
    return { status: 'verified', identity }
  } catch {
    return { status: 'failed', message: IDENTITY_MESSAGES.failed }
  }
}
