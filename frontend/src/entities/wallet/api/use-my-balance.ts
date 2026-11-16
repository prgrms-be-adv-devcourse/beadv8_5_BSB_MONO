import { useQuery } from '@tanstack/react-query'
import { useSyncExternalStore } from 'react'

import { cashApi, getAuthStatus, subscribeAuthStatus, unwrapData } from '@/shared/api'

/**
 * 내 예치금 잔액(GET /api/v1/cash/wallets/balance). 회원 ID는 게이트웨이가 Access Token에서 꺼내
 * X-Member-Id 헤더로 넣어 주므로 프론트가 따로 보내지 않는다.
 * 로그인 상태가 확인된 뒤에만 부른다 (로그아웃 직후 화면이 남아 있는 동안 다시 부르지 않게).
 */
export function useMyBalance() {
  const authStatus = useSyncExternalStore(subscribeAuthStatus, getAuthStatus, () => 'pending')
  const loggedIn = authStatus === 'authenticated'
  // 캐시에는 잔액 조회 훅(cashApi.useGetBalance)과 같은 모양(RsData 그대로)을 넣고, 꺼낼 때만 select로 숫자를 만든다.
  // 같은 키에 숫자를 넣으면 결제 화면(useDepositBalance)이 그 값을 응답으로 읽다 깨진다.
  const balance = useQuery({
    ...cashApi.getGetBalanceQueryOptions(),
    select: (res) => unwrapData(res).balance ?? 0,
    enabled: loggedIn,
  })
  const isError = balance.isError
  return {
    /** 잔액(원). 불러오는 중이거나 실패하면 undefined */
    balance: balance.data,
    isPending: authStatus === 'pending' || (loggedIn && !isError && balance.isPending),
    /** 로그인하지 않았으면 false (불러오지 않는다) */
    loggedIn,
    isError,
    refetch: balance.refetch,
  }
}
