'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Fragment, useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import {
  goToSocialLogin,
  safeNextPath,
  SOCIAL_PROVIDERS,
  socialAuthorizeHref,
  SocialLoginButtons,
  socialLoginErrorMessage,
  socialProviderOf,
  useAuthStatus,
  useLogin,
} from '@/features/auth'
import { ApiError } from '@/shared/api'
import { cn } from '@/shared/lib'
import { BottomCta, Button, Dialog, FOCUS_RING, Logo, TextField } from '@/shared/ui'

import { AUTH_ERRORS, loginSchema, type LoginValues } from '../model/schemas'

const LINK = cn('rounded-sm', FOCUS_RING)

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
        <div className="flex-1" />
        <form
          id="login-form"
          className="flex flex-col items-center px-4"
          noValidate
          onSubmit={handleSubmit((values) =>
            login.mutate(values, { onSuccess: () => router.replace(safeNextPath(next)) }),
          )}
        >
          <Logo className="h-11 w-[150px]" />
          <div className="mt-9 flex w-full flex-col gap-3">
            <TextField
              aria-label="이메일"
              type="email"
              autoComplete="email"
              placeholder="이메일"
              error={errors.email?.message}
              {...register('email', { onChange: clearLoginError })}
            />
            <TextField
              aria-label="비밀번호"
              type="password"
              autoComplete="current-password"
              placeholder="비밀번호"
              error={
                errors.password?.message ??
                (failed ? AUTH_ERRORS.loginFailed : loginErrorMessage(login.error))
              }
              {...register('password', { onChange: clearLoginError })}
            />
            <LinkRow
              className="text-caption-medium text-text-secondary"
              links={[
                {
                  href: next ? `/signup?next=${encodeURIComponent(next)}` : '/signup',
                  label: '회원가입',
                },
                { href: '/find-email', label: '이메일 찾기' },
                { href: '/password-reset', label: '비밀번호 재설정' },
              ]}
            />
          </div>
          <p className="mt-9 mb-3 text-caption-regular text-text-tertiary">SNS 계정으로 시작하기</p>
          <SocialLoginButtons next={next} />
        </form>
        <div className="flex-1" />
        <footer className="flex flex-col items-center gap-2 pt-4 pb-6 text-center">
          <LinkRow
            className="text-caption-medium text-text-secondary"
            links={[
              { href: '/terms/service', label: '이용약관' },
              { href: '/terms/privacy', label: '개인정보처리방침' },
              { href: '/me', label: '고객센터' },
            ]}
          />
          <p className="text-caption-regular text-text-tertiary">
            크루런은 통신판매중개자이며 거래 당사자가 아니에요.
          </p>
        </footer>
      </main>
      <BottomCta
        primary={
          <Button type="submit" form="login-form" size="l" disabled={!isValid || login.isPending}>
            로그인
          </Button>
        }
      />
      <BottomNav />
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
        body="동의 화면에서 취소했거나 연결하지 못했어요. 다시 시도하거나 이메일로 로그인해 주세요."
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

type RowLink = {
  href:
    | '/signup'
    | `/signup?next=${string}`
    | '/find-email'
    | '/password-reset'
    | '/terms/service'
    | '/terms/privacy'
    | '/me'
  label: string
}

// "회원가입   |   이메일 찾기" 처럼 세로선으로 나눈 링크 줄
function LinkRow({ links, className }: { links: RowLink[]; className?: string }) {
  return (
    <p className={cn('flex justify-center gap-3', className)}>
      {links.map((link, i) => (
        <Fragment key={link.href}>
          {i > 0 && <span aria-hidden>|</span>}
          <Link href={link.href} className={LINK}>
            {link.label}
          </Link>
        </Fragment>
      ))}
    </p>
  )
}
