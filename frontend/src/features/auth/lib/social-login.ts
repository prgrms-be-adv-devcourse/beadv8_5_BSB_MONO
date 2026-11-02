import { safeNextPath } from './login-redirect'

/** 소셜 로그인 제공자. 주소에는 소문자, 내 정보 응답(socialProviders)에는 대문자로 온다. */
export const SOCIAL_PROVIDERS = [
  { id: 'kakao', code: 'KAKAO', label: '카카오', object: '카카오를' },
  { id: 'google', code: 'GOOGLE', label: '구글', object: '구글을' },
] as const

export type SocialProviderId = (typeof SOCIAL_PROVIDERS)[number]['id']

const AUTHORIZE_PATH = '/api/v1/auth/oauth2/authorize'

export function socialProviderOf(id: string | null | undefined) {
  return SOCIAL_PROVIDERS.find((p) => p.id === id || p.code === id)
}

/**
 * 소셜 로그인 시작 주소. fetch가 아니라 화면 이동으로 연다(백엔드가 제공자 로그인 화면으로 302를 보낸다).
 * 계정 연결이면 먼저 연결 표 API를 부른다. 서버가 표를 쿠키로 심으므로 주소는 로그인과 같다.
 */
export function socialAuthorizeHref(
  provider: SocialProviderId,
  { next }: { next?: string } = {},
): string {
  const params = new URLSearchParams()
  if (next) params.set('next', safeNextPath(next))
  const query = params.toString()
  return `${AUTHORIZE_PATH}/${provider}${query ? `?${query}` : ''}`
}

/** 화면을 제공자 로그인으로 옮긴다. 테스트에서 바꿔 끼울 수 있게 따로 둔다. */
export function goToSocialLogin(href: string) {
  window.location.assign(href)
}

/**
 * 소셜 로그인 콜백이 로그인 화면에 붙인 오류(?error=)를 안내 문구로 바꾼다.
 * 같은 이메일이면 자동으로 연결하지 않으므로, 원래 방법으로 로그인한 뒤 계정 연결하는 길을 알려 준다.
 */
export function socialLoginErrorMessage(
  error: string | undefined,
  provider?: string,
  joinedWith?: string,
): string | undefined {
  if (!error) return undefined
  if (error === 'AUTH_SOCIAL_EMAIL_TAKEN') {
    const target = socialProviderOf(provider)
    const joined = socialProviderOf(joinedWith)
    const how = joined ? `${joined.label}로` : '이메일로'
    const head = joined ? `${joined.label}로 가입한 이메일이에요.` : '이메일로 가입한 계정이에요.'
    const link = target ? ` 마이 > 계정 연결에서 ${target.object} 연결할 수 있어요.` : ''
    return `${head} ${how} 로그인해 주세요.${link}`
  }
  return '소셜 로그인을 끝내지 못했어요. 다시 시도해 주세요.'
}

/** 계정 연결 화면에 돌아온 결과(?linked=, ?error=)를 안내 문구로 바꾼다. */
export function socialLinkResultMessage(
  linked: string | undefined,
  error: string | undefined,
): { tone: 'success' | 'danger'; message: string } | undefined {
  if (linked) {
    const provider = socialProviderOf(linked)
    return { tone: 'success', message: `${provider?.label ?? '소셜'} 계정을 연결했어요.` }
  }
  if (!error) return undefined
  switch (error) {
    case 'AUTH_SOCIAL_ALREADY_LINKED':
      return { tone: 'danger', message: '이미 다른 회원에 연결된 소셜 계정이에요.' }
    case 'MEMBER_SOCIAL_PROVIDER_ALREADY_LINKED':
      return { tone: 'danger', message: '이미 연결된 소셜 로그인이에요.' }
    default:
      return { tone: 'danger', message: '계정을 연결하지 못했어요. 다시 시도해 주세요.' }
  }
}
