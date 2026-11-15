import { useMutation, useQueryClient } from '@tanstack/react-query'

import { memberApi, memberModel, unwrapData } from '@/shared/api'

import {
  goToSocialLogin,
  rememberLinkingProvider,
  socialAuthorizeHref,
  type SocialProviderId,
} from '../lib/social-login'
import { applyLogin } from './use-login'

/**
 * 소셜 가입 (POST /api/v1/auth/oauth2/join). 가입 대기 쿠키(httpOnly)로 어느 소셜 계정인지 서버가 안다.
 * 응답은 로그인과 같아서(Access Token + 회원 정보, Refresh Token 쿠키) 성공하면 바로 로그인 상태다.
 */
export function useSocialJoin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (body: memberModel.SocialJoinRequestDto) => {
      const { accessToken, member } = unwrapData(await memberApi.socialJoin(body))
      if (!accessToken) throw new Error('가입 응답에 Access Token이 없습니다.')
      return { accessToken, member }
    },
    onSuccess: ({ accessToken, member }) => applyLogin(queryClient, accessToken, member),
  })
}

/**
 * 로그인한 회원의 소셜 계정 연결을 시작한다.
 * Access Token은 화면 이동에 실을 수 없으므로 먼저 2분짜리 1회용 연결 표를 받는다.
 * 표는 서버가 httpOnly 쿠키로 심어서 프론트는 만지지 않는다. 이어서 같은 브라우저로 시작 주소를 열면 연결로 처리된다.
 * 연결 결과는 제공자 화면을 다녀온 뒤 계정 연결 화면 주소(?linked=, ?error=)로 알 수 있다.
 */
export function useLinkSocial() {
  return useMutation({
    mutationFn: async (provider: SocialProviderId) => {
      await memberApi.issueSocialLinkTicket(provider)
      rememberLinkingProvider(provider)
      goToSocialLogin(socialAuthorizeHref(provider, { next: '/me/accounts' }))
    },
  })
}

/** 소셜 계정 연결 해제. 응답의 회원 정보로 내 정보 캐시를 바꿔 화면이 바로 바뀐다. */
export function useUnlinkSocial() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (provider: SocialProviderId) =>
      unwrapData(await memberApi.unlinkSocialAccount(provider)),
    onSuccess: (member) => {
      queryClient.setQueryData<memberModel.RsDataMemberDto>(memberApi.getFindMeQueryKey(), {
        status: 200,
        message: '연결을 해제했습니다.',
        data: member,
      })
    },
  })
}
