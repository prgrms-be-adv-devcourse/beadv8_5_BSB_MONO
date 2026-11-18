import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { memberApi, unwrapData } from '@/shared/api'

import { applyLogin } from './use-login'

/**
 * 발표 시연용 데모 로그인이 서버에서 켜져 있는지 (GET /api/v1/auth/demo-login).
 * 켜져 있으면 200, 꺼져 있으면 404라 성공했을 때만 버튼을 보여 준다. 404는 다시 묻지 않는다.
 */
export function useDemoLoginAvailable(): boolean {
  return useQuery({ ...memberApi.getDemoLoginAvailabilityQueryOptions(), retry: false }).isSuccess
}

/**
 * 데모 로그인 (POST /api/v1/auth/demo-login). 본문 없이 부르고, 로그인할 계정은 서버 설정이 정한다.
 * 비밀번호를 프론트에 두지 않는다. 응답은 이메일 로그인과 같아 같은 방식으로 반영한다.
 */
export function useDemoLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      // 서버는 JSON 형식 요청만 받는다(다른 사이트의 폼 전송을 막으려고). 본문은 보내지 않는다
      const { accessToken, member } = unwrapData(
        await memberApi.demoLogin({ headers: { 'Content-Type': 'application/json' } }),
      )
      if (!accessToken) throw new Error('로그인 응답에 Access Token이 없습니다.')
      return { accessToken, member }
    },
    onSuccess: ({ accessToken, member }) => applyLogin(queryClient, accessToken, member),
  })
}
