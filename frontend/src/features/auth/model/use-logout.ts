import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'

import { clearAccessToken, memberApi } from '@/shared/api'

import { reloadToLogin } from '../lib/login-redirect'

/**
 * 로그아웃한다 (POST /api/v1/auth/logout). 서버가 Refresh Token을 지우고 쿠키를 만료시킨다.
 * 성공하면 문서를 새로 불러 로그인 화면으로 간다. 토큰을 여기서 비우면 지금 화면이 로그아웃 상태로
 * 잠깐 다시 그려지기 때문이다. 새로 불러오면 메모리의 토큰과 서버 데이터 캐시가 함께 사라진다.
 * 요청이 실패하면 서버 쿠키가 남아 새로 불러올 때 다시 로그인되므로, 이 브라우저에서 토큰과 캐시를 직접 지운다.
 */
export function useLogout() {
  const queryClient = useQueryClient()
  const router = useRouter()
  return useMutation({
    mutationFn: () => memberApi.logout(),
    onSuccess: reloadToLogin,
    onError: () => {
      clearAccessToken()
      queryClient.removeQueries()
      router.replace('/login')
    },
  })
}
