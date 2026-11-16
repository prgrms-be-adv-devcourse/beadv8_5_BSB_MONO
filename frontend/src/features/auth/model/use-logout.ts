import { useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'

import { clearAccessToken, memberApi } from '@/shared/api'
import { clearRecentSearches } from '@/shared/lib'

import { reloadAfterLogout } from '../lib/login-redirect'

/**
 * 문서를 새로 불러 떠나기 직전에 건다. 페이지를 떠날 때 토큰과 캐시를 지운다.
 * 새로 불러와도 그 앞의 방문 기록은 이전 문서 것이라, 브라우저가 이전 문서를 메모리째 보관했다가
 * 뒤로 가기로 되살리면 앞사람의 토큰과 데이터가 보인다. 떠난 뒤에 지우므로 화면에는 보이지 않는다.
 */
export function clearOnPageHide(queryClient: QueryClient) {
  window.addEventListener(
    'pagehide',
    () => {
      clearAccessToken()
      queryClient.clear()
      // 최근 검색어는 이 브라우저에 남으므로, 같은 브라우저의 다음 사람에게 보이지 않게 함께 지운다
      clearRecentSearches()
    },
    { once: true },
  )
}

/**
 * 로그아웃한다 (POST /api/v1/auth/logout). 서버가 Refresh Token을 지우고 쿠키를 만료시킨다.
 * 성공하면 문서를 새로 불러 홈으로 간다(reloadAfterLogout). 토큰을 여기서 비우면 지금 화면이 로그아웃 상태로
 * 잠깐 다시 그려지기 때문이다. 새로 불러오면 메모리의 토큰과 서버 데이터 캐시가 함께 사라진다.
 * 요청이 실패하면 서버 쿠키가 남아 새로 불러올 때 다시 로그인되므로, 이 브라우저에서 토큰과 캐시를 직접 지운다.
 */
export function useLogout() {
  const queryClient = useQueryClient()
  const router = useRouter()
  return useMutation({
    mutationFn: () => memberApi.logout(),
    onSuccess: () => {
      clearOnPageHide(queryClient)
      reloadAfterLogout()
    },
    onError: () => {
      clearAccessToken()
      clearRecentSearches()
      queryClient.removeQueries()
      router.replace('/')
    },
  })
}
