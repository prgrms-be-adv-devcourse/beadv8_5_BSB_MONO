'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import {
  goToSocialLogin,
  safeNextPath,
  SOCIAL_PROVIDERS,
  socialAuthorizeHref,
  SocialLoginButtons,
  socialLoginErrorMessage,
  socialProviderOf,
  useAuthStatus,
  useDemoLogin,
  useDemoLoginAvailable,
  useLogin,
} from '@/features/auth'
import { ApiError } from '@/shared/api'
import { cn } from '@/shared/lib'
import { Button, buttonClassName, Dialog, FOCUS_RING, Logo, TextField } from '@/shared/ui'

import { AUTH_ERRORS, loginSchema, type LoginValues } from '../model/schemas'

// 피그마 V2 · M03 로그인 (+ M03-E 로그인 실패)
// failed는 시안 미리보기(?preview=error)용. 실패 문구를 띄운 채로 보여 준다.
// next는 로그인 뒤 돌아갈 경로(?next=). 앱 내부 경로가 아니면 홈으로 간다.
// social은 소셜 로그인 콜백이 붙여 보낸 결과(?error=&provider=&joinedWith=). 같은 이메일이면 연결 방법을 안내한다.
export function LoginPage({
  failed = false,
  next,
  social,
}: {
  failed?: boolean
  next?: string
  social?: { error?: string; provider?: string; joinedWith?: string }
}) {
  const router = useRouter()
  const login = useLogin()
  // 발표 시연용. 서버에서 켜져 있을 때만 버튼을 보여 준다
  const demoAvailable = useDemoLoginAvailable()
  const demoLogin = useDemoLogin()
  // 이미 로그인한 사람이 로그인 화면에 오면 가려던 곳으로 바로 보낸다
  const authStatus = useAuthStatus()
  useEffect(() => {
    if (authStatus === 'authenticated') router.replace(safeNextPath(next))
  }, [authStatus, next, router])
  const {
    register,
    handleSubmit,
    formState: { errors, isValid },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    mode: 'onTouched',
    defaultValues: failed
      ? { email: 'runner@crewrun.kr', password: 'password1' }
      : { email: '', password: '' },
  })
  // 서버가 준 로그인 실패 문구는 입력을 고치면 지운다 (칸별 입력 검사 문구는 그대로 둔다).
  // 요청 중에 지우면 성공해도 화면 이동 콜백이 불리지 않으므로 실패했을 때만 지운다.
  const clearLoginError = () => {
    if (login.isError) login.reset()
  }
  const socialError = socialLoginErrorMessage(social?.error, social?.provider, social?.joinedWith)
  const [socialDismissed, setSocialDismissed] = useState(false)
  // 비밀번호 없는 소셜 회원이 이메일로 로그인하면 서버가 가입한 제공자를 알려 준다 (피그마 M03-S4 모달).
  const socialAccount =
    login.error instanceof ApiError && login.error.code === 'AUTH_SOCIAL_ACCOUNT'
      ? socialProviderOf(login.error.serverMessage?.startsWith('구글') ? 'google' : 'kakao')
      : undefined
  const failedProvider = socialProviderOf(social?.provider)
  const retryProvider = failedProvider ?? SOCIAL_PROVIDERS[0]
  return (
    <>
      <AppHeader type="sub" title="로그인" />
      <main className="flex flex-1 flex-col bg-bg-surface">
        <div className="flex flex-1 flex-col justify-center py-8">
          <form
            id="login-form"
            className="flex flex-col items-center px-4"
            noValidate
            onSubmit={handleSubmit((values) =>
              login.mutate(values, { onSuccess: () => router.replace(safeNextPath(next)) }),
            )}
          >
            <Logo className="h-11 w-36" />
            <div className="mt-12 flex w-full flex-col gap-3">
              <TextField
                aria-label="이메일"
                type="email"
                autoComplete="email"
                placeholder="이메일"
                error={errors.email?.message}
                hideErrorBorder
                {...register('email', { onChange: clearLoginError })}
              />
              <TextField
                aria-label="비밀번호"
                type="password"
                autoComplete="current-password"
                placeholder="비밀번호"
                hideErrorBorder
                error={
                  errors.password?.message ??
                  (failed ? AUTH_ERRORS.loginFailed : loginErrorMessage(login.error))
                }
                {...register('password', { onChange: clearLoginError })}
              />
              <Button type="submit" size="l" disabled={!isValid || login.isPending}>
                로그인
              </Button>
              {/* 입력칸과 헷갈리지 않게 테두리 없는 글자 링크로 둔다 (CRU-124) */}
              <div className="flex items-center justify-center gap-4">
                <Link
                  href={next ? `/signup?next=${encodeURIComponent(next)}` : '/signup'}
                  className={buttonClassName({ variant: 'text', size: 's' })}
                >
                  회원가입
                </Link>
                <span aria-hidden className="h-4 w-px bg-border-default" />
                <Link
                  href="/find-email"
                  className={buttonClassName({ variant: 'text', size: 's' })}
                >
                  계정 찾기
                </Link>
              </div>
              {demoAvailable && (
                <>
                  <Button
                    type="button"
                    variant="secondary"
                    size="m"
                    className="bg-bg-subtle inset-ring-0 enabled:hover:bg-bg-subtle-hover"
                    disabled={demoLogin.isPending}
                    onClick={() =>
                      demoLogin.mutate(undefined, {
                        onSuccess: () => router.replace(safeNextPath(next)),
                      })
                    }
                  >
                    테스트 계정으로 로그인
                  </Button>
                  {demoLogin.isError && (
                    <p role="alert" className="text-center text-body1-regular text-text-danger">
                      {(demoLogin.error instanceof ApiError && demoLogin.error.serverMessage) ||
                        AUTH_ERRORS.loginUnavailable}
                    </p>
                  )}
                </>
              )}
            </div>
            <p className="mt-8 mb-3 text-caption-regular text-text-secondary">
              SNS 계정으로 시작하기
            </p>
            <SocialLoginButtons next={next} />
          </form>
        </div>
        <footer className="flex items-center justify-center gap-2 pb-6">
          {FOOTER_LINKS.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={cn(
                'flex h-11 items-center rounded-sm px-2 text-caption-medium text-text-secondary underline',
                FOCUS_RING,
              )}
            >
              {link.label}
            </Link>
          ))}
        </footer>
      </main>
      {/* M03-S3 같은 이메일 계정 있음 */}
      <Dialog
        open={!!socialError && social?.error === 'AUTH_SOCIAL_EMAIL_TAKEN' && !socialDismissed}
        onOpenChange={(open) => !open && setSocialDismissed(true)}
        type="alert"
        title="이메일로 가입한 계정이에요"
        body={socialError}
        confirmLabel="확인"
        onConfirm={() => setSocialDismissed(true)}
      />
      {/* M03-S2 소셜 로그인 실패하거나 취소함 */}
      <Dialog
        open={!!socialError && social?.error !== 'AUTH_SOCIAL_EMAIL_TAKEN' && !socialDismissed}
        onOpenChange={(open) => !open && setSocialDismissed(true)}
        title={`${failedProvider?.label ?? '소셜'} 로그인을 마치지 못했어요`}
        body={`${failedProvider?.label ?? '소셜'}${failedProvider?.id === 'google' ? '과' : '와'} 연결하지 못했어요.`}
        cancelLabel="닫기"
        confirmLabel="다시 시도하기"
        onConfirm={() => goToSocialLogin(socialAuthorizeHref(retryProvider.id, { next }))}
      />
      {/* M03-S4 이메일 로그인 · 소셜로 가입한 계정 */}
      <Dialog
        open={!!socialAccount}
        onOpenChange={(open) => !open && login.reset()}
        title={`${socialAccount?.label}로 가입한 계정이에요`}
        body={login.error instanceof ApiError ? login.error.serverMessage : undefined}
        cancelLabel="닫기"
        confirmLabel={`${socialAccount?.label}로 시작하기`}
        onConfirm={() =>
          socialAccount && goToSocialLogin(socialAuthorizeHref(socialAccount.id, { next }))
        }
      />
    </>
  )
}

/**
 * 로그인 실패 문구. 401(이메일·비밀번호 불일치)은 시안(M03-E) 문구, 그 밖에는 서버 문구를 보여 준다.
 * 비밀번호 없는 소셜 회원(AUTH_SOCIAL_ACCOUNT)은 모달로 안내하므로 칸 문구는 없다.
 */
function loginErrorMessage(error: Error | null): string | undefined {
  if (!error) return undefined
  if (error instanceof ApiError && error.code === 'AUTH_SOCIAL_ACCOUNT') return undefined
  if (error instanceof ApiError && error.status === 401) return AUTH_ERRORS.loginFailed
  return (error instanceof ApiError && error.serverMessage) || AUTH_ERRORS.loginUnavailable
}

const FOOTER_LINKS = [
  { href: '/terms/service', label: '이용약관' },
  { href: '/terms/privacy', label: '개인정보처리방침' },
  { href: '/me/support', label: '고객센터' },
] as const
