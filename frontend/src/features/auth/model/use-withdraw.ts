import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiError, memberApi } from '@/shared/api'

import { reloadTo } from '../lib/login-redirect'
import { clearOnPageHide } from './use-logout'

/**
 * 회원 탈퇴 (DELETE /api/v1/member/members/me). Refresh Token은 서버가 지운다.
 * 성공하면 문서를 새로 불러 탈퇴 완료 화면으로 간다. 새로 불러오면 메모리의 토큰과 앞 사람의 캐시가 사라지고,
 * 재발급이 실패해 로그아웃 상태가 된다. 여기서 토큰을 먼저 비우면 로그인 확인 장치가 탈퇴 화면을 내리고
 * 로그인 화면으로 보내, 완료 화면으로 가지 못한다.
 */
export function useWithdraw() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => memberApi.withdrawMe(),
    onSuccess: () => {
      clearOnPageHide(queryClient)
      reloadTo('/me/withdraw/done')
    },
  })
}

/** 탈퇴 실패 이유를 서버 code로 가른다. 모르는 code는 other. */
export type WithdrawFailure = 'cash-left' | 'ticket-left' | 'crew-leader' | 'check-failed' | 'other'

export function withdrawFailure(error: unknown): WithdrawFailure {
  if (!(error instanceof ApiError)) return 'other'
  switch (error.code) {
    case 'MEMBER_WITHDRAW_CASH_LEFT':
      return 'cash-left'
    case 'MEMBER_WITHDRAW_TICKET_LEFT':
      return 'ticket-left'
    case 'MEMBER_WITHDRAW_CREW_LEADER':
      return 'crew-leader'
    case 'MEMBER_WITHDRAW_CHECK_FAILED':
      return 'check-failed'
    default:
      return 'other'
  }
}
