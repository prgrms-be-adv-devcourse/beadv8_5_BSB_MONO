'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useForm, useWatch, type UseFormRegister } from 'react-hook-form'
import { z } from 'zod'

import { AppHeader } from '@/widgets/app-header'
import { safeNextPath, socialProviderOf, useAuthStatus, useSocialJoin } from '@/features/auth'
import { ApiError, memberApi, unwrapData } from '@/shared/api'
import { BottomCta, Button, Spinner, Stepper, TextField } from '@/shared/ui'

import { joinErrorMessage } from '../model/join'
import {
  signupAccountSchema,
  signupProfileSchema,
  type SignupProfileValues,
} from '../model/schemas'
import { useSignupStep } from '../model/signup-store'
import { agreedTermTypes } from '../model/signup-terms'
import { useSignupVerification } from '../model/use-signup-verification'
import { CenterBody, StepIntro } from './parts'
import { SignupProfileFields, SignupTermsSheet, VerificationNeededHint } from './SignupPages'

const FAILED_LOGIN = '/login?error=AUTH_SOCIAL_FAILED'

/**
 * 소셜 로그인 완료 (/auth/social/complete). 백엔드 콜백이 Refresh Token 쿠키만 심고 이 화면으로 보낸다.
 * Access Token은 주소에 싣지 않으므로, 앱 시작 때 도는 재발급(AuthSessionRestorer)이 쿠키로 받아 온다.
 * 재발급이 끝나 로그인 상태가 되면 next로, 실패하면 로그인 화면으로 보낸다.
 */
export function SocialCompletePage({ next }: { next?: string }) {
  const router = useRouter()
  const status = useAuthStatus()
  useEffect(() => {
    if (status === 'authenticated') router.replace(safeNextPath(next))
    if (status === 'anonymous') router.replace(FAILED_LOGIN)
  }, [status, next, router])
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-3 bg-bg-surface">
      <Spinner />
      <p className="text-body-m text-text-secondary">로그인하고 있어요</p>
    </main>
  )
}

/**
 * 소셜 가입 (/signup/social). 소셜로 처음 온 사람이 약관 → 회원 정보 두 단계로 가입한다.
 * 어느 소셜 계정인지는 백엔드가 심은 가입 대기 쿠키(httpOnly, 15분)로 서버가 안다.
 * 제공자가 확인한 이메일이 있으면 고칠 수 없게 보여 주고, 없으면 직접 입력한다.
 */
export function SocialSignupPage({ next }: { next?: string }) {
  const router = useRouter()
  const info = memberApi.useFindSocialSignup({ query: { select: unwrapData, retry: false } })
  // 가입 대기가 없거나 지났으면(401) 소셜 로그인부터 다시 하게 로그인 화면으로 보낸다
  useEffect(() => {
    if (info.isError) router.replace(FAILED_LOGIN)
  }, [info.isError, router])

  if (!info.data) {
    return (
      <main className="flex flex-1 items-center justify-center bg-bg-surface">
        <Spinner />
      </main>
    )
  }
  return (
    <SocialSignupForm
      next={next}
      provider={info.data.provider}
      lockedEmail={info.data.emailLocked ? (info.data.email ?? undefined) : undefined}
      name={info.data.name ?? undefined}
    />
  )
}

const STEPS = ['약관', '회원 정보']

type SocialSignupValues = SignupProfileValues & { email: string }

function SocialSignupForm({
  next,
  provider,
  lockedEmail,
  name,
}: {
  next?: string
  provider: string
  lockedEmail?: string
  name?: string
}) {
  const router = useRouter()
  const terms = useSignupStep((s) => s.terms)
  const termsAgreed = useSignupStep((s) => s.termsAgreed)
  const [termsOpen, setTermsOpen] = useState(!termsAgreed)
  const join = useSocialJoin()
  const [formError, setFormError] = useState<string>()
  const providerLabel = socialProviderOf(provider)?.label ?? '소셜'

  // 이메일을 제공자가 확인해 줬으면 검사하지 않는다(서버가 그 값을 쓴다)
  const schema = useMemo(
    () =>
      signupProfileSchema.extend({
        email: lockedEmail ? z.string() : signupAccountSchema.shape.email,
      }),
    [lockedEmail],
  )
  const {
    register,
    handleSubmit,
    setError,
    setValue,
    control,
    formState: { errors, isValid, isSubmitting },
  } = useForm<SocialSignupValues>({
    resolver: zodResolver(schema),
    mode: 'onTouched',
    defaultValues: {
      email: lockedEmail ?? '',
      name: name ?? '',
      birth: '',
      phone: '',
      nickname: '',
    },
  })

  const { verification, onVerified, reset } = useSignupVerification(setValue)
  const phone = useWatch({ control, name: 'phone' })

  const onSubmit = handleSubmit((values) => {
    // 가입 버튼은 본인 확인 뒤에만 켜지지만, Enter 제출도 같은 조건으로 막는다
    if (!verification) return
    setFormError(undefined)
    join.mutate(
      {
        email: lockedEmail ? undefined : values.email.trim(),
        name: values.name.trim(),
        nickname: values.nickname.trim(),
        phone: values.phone.trim(),
        birthDate: values.birth.trim(),
        agreedTerms: agreedTermTypes(terms),
        verificationToken: verification.verificationToken,
      },
      {
        onSuccess: () => router.replace(safeNextPath(next)),
        onError: (error) => {
          if (error instanceof ApiError && error.code === 'AUTH_SOCIAL_SIGNUP_EXPIRED') {
            router.replace(FAILED_LOGIN)
            return
          }
          if (error instanceof ApiError && error.code === 'AUTH_SOCIAL_ALREADY_LINKED') {
            setFormError('이미 가입된 소셜 계정이에요. 로그인 화면에서 다시 로그인해 주세요.')
            return
          }
          // 같은 사람이 이메일이 다른 소셜 계정으로 다시 가입하려는 경우다. 새 회원 대신 계정 연결로 안내한다
          if (error instanceof ApiError && error.code === 'AUTH_SOCIAL_PHONE_TAKEN') {
            setError('phone', { message: '이 번호로 가입한 계정이 있어요.' }, { shouldFocus: true })
            setFormError(
              `원래 쓰던 방법으로 로그인한 뒤 마이 → 계정 연결에서 ${providerLabel} 계정을 연결해 주세요.`,
            )
            return
          }
          if (error instanceof ApiError && error.code === 'AUTH_SOCIAL_EMAIL_REQUIRED') {
            setError('email', { message: '이메일을 입력해 주세요.' }, { shouldFocus: true })
            return
          }
          const { target, message } = joinErrorMessage(error)
          if (target === 'verification') reset()
          if (target === 'form' || target === 'verification') setFormError(message)
          else setError(target, { message }, { shouldFocus: true })
        },
      },
    )
  })
  // 약관 시트를 동의 없이 닫았다면 "가입 완료"가 시트를 다시 연다
  const onFormSubmit = (event: FormEvent<HTMLFormElement>) => {
    if (termsAgreed) return onSubmit(event)
    event.preventDefault()
    setTermsOpen(true)
  }
  const submitting = isSubmitting || join.isPending || join.isSuccess

  return (
    <>
      <form noValidate onSubmit={onFormSubmit} className="contents">
        <AppHeader type="sub" title={`${providerLabel}로 가입하기`} />
        <div className="bg-bg-surface px-4 pt-3 pb-1">
          <Stepper steps={STEPS} current={termsAgreed ? 1 : 0} />
        </div>
        <CenterBody center={false}>
          <StepIntro
            title="회원 정보를 적어요"
            description={`${providerLabel} 계정으로 가입해요. 대회 신청에 필요한 정보만 받아요.`}
          />
          {lockedEmail ? (
            <TextField
              label="이메일"
              value={lockedEmail}
              readOnly
              helper={`${providerLabel}에서 확인한 이메일이에요`}
            />
          ) : (
            <TextField
              label="이메일"
              type="email"
              autoComplete="email"
              placeholder="이메일을 입력해 주세요"
              error={errors.email?.message}
              {...register('email')}
            />
          )}
          {/* 회원 정보 칸은 이메일 가입과 같다. 이메일 칸 하나가 더 있을 뿐이라 같은 등록 함수를 넘긴다 */}
          <SignupProfileFields
            register={register as unknown as UseFormRegister<SignupProfileValues>}
            errors={errors}
            phone={phone}
            verification={verification}
            onVerified={onVerified}
            onResetVerification={reset}
          />
          {formError && (
            <p role="alert" className="text-caption text-text-danger">
              {formError}
            </p>
          )}
        </CenterBody>
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            <div className="flex flex-col gap-2">
              <VerificationNeededHint show={!verification} />
              <Button
                type="submit"
                size="l"
                disabled={!isValid || !verification}
                loading={submitting}
              >
                가입 완료
              </Button>
            </div>
          }
        />
      </form>
      <SignupTermsSheet open={termsOpen} onOpenChange={setTermsOpen} />
    </>
  )
}
