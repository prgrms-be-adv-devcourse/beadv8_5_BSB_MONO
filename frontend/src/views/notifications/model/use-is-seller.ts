import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData } from '@/shared/api'

/**
 * 판매자 승인을 받은 회원인지. 마이 화면 판매자 줄과 같은 내 판매자 신청 조회(GET /api/v1/member/sellers/me)를 쓴다.
 * 심사 중 · 반려 · 오류 · 신청 전(404)은 판매자가 아니다. pending은 아직 확인 중.
 */
export function useIsSeller() {
  const mine = memberApi.useFindMine({
    // 신청 전 404는 다시 불러도 같아서 재시도하지 않는다
    query: { select: unwrapData, enabled: useAuthStatus() === 'authenticated', retry: false },
  })
  return { isSeller: mine.data?.status === 'APPROVED', pending: mine.isPending }
}
