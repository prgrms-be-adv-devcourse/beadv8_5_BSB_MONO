import { useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query'

import { memberApi, memberModel, setAccessToken, unwrapData } from '@/shared/api'

/**
 * 이메일·비밀번호로 로그인한다 (POST /api/v1/auth/login).
 * Access Token은 메모리에 두고, Refresh Token은 서버가 httpOnly 쿠키로 심는다.
 * 응답의 회원 정보는 내 정보 조회 캐시에 넣어 바로 다시 부르지 않게 한다.
 */
export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (values: memberModel.MemberLoginRequestDto) => {
      const { accessToken, member } = unwrapData(await memberApi.login(values))
      if (!accessToken) throw new Error('로그인 응답에 Access Token이 없습니다.')
      return { accessToken, member }
    },
    onSuccess: ({ accessToken, member }) => applyLogin(queryClient, accessToken, member),
  })
}

/**
 * 로그인 응답(Access Token + 회원 정보)을 반영한다. 이메일 로그인과 소셜 가입이 함께 쓴다.
 * 세션 만료(재발급 실패)로 로그아웃 없이 다른 회원이 로그인해도 이전 회원의 캐시(잔액 등)가 남지 않게 비운다.
 */
export function applyLogin(
  queryClient: QueryClient,
  accessToken: string,
  member: memberModel.MemberDto | undefined,
) {
  setAccessToken(accessToken)
  queryClient.removeQueries()
  if (member) {
    queryClient.setQueryData<memberModel.RsDataMemberDto>(memberApi.getFindMeQueryKey(), {
      status: 200,
      message: '로그인 성공',
      data: member,
    })
  }
}
