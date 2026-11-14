'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useForm, useWatch, type UseFormRegister } from 'react-hook-form'
import { z } from 'zod'

import { AppHeader } from '@/widgets/app-header'
import {
  safeNextPath,
  SocialMark,
  socialProviderOf,
  useAuthStatus,
  useSocialJoin,
} from '@/features/auth'
import {
  IdentityVerificationBand,
  LockedIdentityFields,
  rejoinInfoOf,
  type RejoinInfo,
} from '@/features/identity-verification'
import { ApiError, memberApi, unwrapData } from '@/shared/api'
import {
  BottomCta,
  Button,
  buttonClassName,
  Divider,
  EmptyState,
  Spinner,
  TextField,
} from '@/shared/ui'

import { joinErrorMessage } from '../model/join'
import {
  signupAccountSchema,
  signupProfileSchema,
  type SignupProfileValues,
} from '../model/schemas'
import { clearSignupDraft, saveSignupDraft, takeSignupDraft } from '../model/signup-draft'
import { useSignupStep } from '../model/signup-store'
import { agreedTermTypes } from '../model/signup-terms'
import { CenterBody, StepIntro } from './parts'
import {
  DuplicateIdentityDialog,
  RejoinTooSoonDialog,
  SignupProfileFields,
  SignupTermsSheet,
} from './SignupPages'

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
  // 시안 M03-S1: 어느 제공자인지 주소에 없어 「소셜 계정」으로 부른다
  return (
    <>
      <AppHeader type="sub" title="로그인" />
      <main className="flex flex-1 items-center justify-center bg-bg-surface">
        <EmptyState
          role="status"
          title="소셜 계정을 확인하고 있어요"
          description="잠시만 기다려 주세요. 확인이 끝나면 자동으로 이동해요."
        />
      </main>
    </>
  )
}

/**
 * 소셜 가입 (/signup/social). 소셜로 처음 온 사람이 약관 → 회원 정보 두 단계로 가입한다.
 * 어느 소셜 계정인지는 백엔드가 심은 가입 대기 쿠키(httpOnly, 15분)로 서버가 안다.
 * 제공자가 확인한 이메일이 있으면 고칠 수 없게 보여 주고, 없으면 직접 입력한다.
 */
export function SocialSignupPage({ next }: { next?: string }) {
  const info = memberApi.useFindSocialSignup({ query: { select: unwrapData, retry: false } })
  // 가입 대기가 없거나 지났으면(401) 「가입 시간이 지났어요」를 보여 준다. 가입을 누른 뒤 지난 경우도 같다.
  const [expired, setExpired] = useState(false)

  if (info.isError || expired) return <SocialSignupExpired />
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
      onExpired={() => setExpired(true)}
      provider={info.data.provider}
      lockedEmail={info.data.emailLocked ? (info.data.email ?? undefined) : undefined}
      name={info.data.name ?? undefined}
    />
  )
}

// 시안 M04-SE 소셜 가입 · 가입 시간 지남. 소셜 로그인으로 돌아온 뒤 15분 안에 가입을 마치지 못했을 때
function SocialSignupExpired() {
  return (
    <>
      <AppHeader type="sub" title="가입하기" />
      <main className="flex flex-1 items-center justify-center bg-bg-surface">
        <EmptyState
          role="alert"
          title="가입 시간이 지났어요"
          description="소셜 로그인으로 돌아온 뒤 15분 안에 가입을 마치지 못했어요. 처음부터 다시 시작해 주세요."
          action={
            <Link href="/login" className={buttonClassName({ variant: 'secondary' })}>
              로그인 화면으로
            </Link>
          }
        />
      </main>
    </>
  )
}

type SocialSignupValues = SignupProfileValues & { email: string }

function SocialSignupForm({
  next,
  onExpired,
  provider,
  lockedEmail,
  name,
}: {
  next?: string
  onExpired: () => void
  provider: string
  lockedEmail?: string
  name?: string
}) {
  const router = useRouter()
  const terms = useSignupStep((s) => s.terms)
  const termsAgreed = useSignupStep((s) => s.termsAgreed)
  const account = useSignupStep((s) => s.account)
  const profile = useSignupStep((s) => s.profile)
  const verification = useSignupStep((s) => s.verification)
  const setVerification = useSignupStep((s) => s.setVerification)
  const restoreDraft = useSignupStep((s) => s.restoreDraft)
  const [termsOpen, setTermsOpen] = useState(!termsAgreed)
  const [duplicate, setDuplicate] = useState(false)
  const [rejoin, setRejoin] = useState<RejoinInfo | null>(null)
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
    getValues,
    control,
    formState: { errors, isValid, isSubmitting },
  } = useForm<SocialSignupValues>({
    resolver: zodResolver(schema),
    mode: 'onTouched',
    // 모바일 본인인증에서 돌아오면 남겨 둔 이메일 · 닉네임으로 시작한다
    defaultValues: {
      email: lockedEmail ?? account.email,
      nickname: profile.nickname,
      gender: '',
    },
  })
  const gender = useWatch({ control, name: 'gender' })

  const onSubmit = handleSubmit((values) => {
    // 가입 버튼은 본인인증 뒤에만 켜지지만, Enter 제출도 같은 조건으로 막는다
    if (!verification) return
    setFormError(undefined)
    join.mutate(
      {
        email: lockedEmail ? undefined : values.email.trim(),
        nickname: values.nickname.trim(),
        gender: values.gender || undefined,
        agreedTerms: agreedTermTypes(terms),
        verificationToken: verification.verificationToken,
      },
      {
        onSuccess: () => router.replace(safeNextPath(next)),
        onError: (error) => {
          if (error instanceof ApiError && error.code === 'AUTH_SOCIAL_SIGNUP_EXPIRED') {
            onExpired()
            return
          }
          if (error instanceof ApiError && error.code === 'AUTH_SOCIAL_ALREADY_LINKED') {
            setFormError('이미 가입된 소셜 계정이에요. 로그인 화면에서 다시 로그인해 주세요.')
            return
          }
          // 같은 사람이 이메일이 다른 소셜 계정으로 다시 가입하려는 경우다. 새 회원 대신 계정 연결로 안내한다
          if (error instanceof ApiError && error.code === 'AUTH_SOCIAL_PHONE_TAKEN') {
            setFormError(
              `이 번호로 가입한 계정이 있어요. 원래 쓰던 방법으로 로그인한 뒤 마이 → 계정 연결에서 ${providerLabel} 계정을 연결해 주세요.`,
            )
            return
          }
          if (error instanceof ApiError && error.code === 'AUTH_SOCIAL_EMAIL_REQUIRED') {
            setError('email', { message: '이메일을 입력해 주세요.' }, { shouldFocus: true })
            return
          }
          const { target, message } = joinErrorMessage(error)
          if (target === 'duplicate') return setDuplicate(true)
          if (target === 'rejoinTooSoon') return setRejoin(rejoinInfoOf(error))
          if (target === 'nickname') return setError(target, { message }, { shouldFocus: true })
          if (target === 'verification') setVerification(null)
          setFormError(message)
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
        <AppHeader type="sub" title="가입하기" />
        <p className="flex items-center bg-bg-brand-subtle px-4 py-3">
          <SocialMark id={socialProviderOf(provider)?.id ?? 'kakao'} />
          <span className="sr-only">{providerLabel} 계정으로 가입하고 있어요</span>
        </p>
        <CenterBody center={false}>
          <IdentityVerificationBand
            verification={verification}
            onVerified={setVerification}
            onDuplicate={() => setDuplicate(true)}
            onRejoinTooSoon={setRejoin}
            onBeforeOpen={() => {
              saveSignupDraft({
                terms,
                termsAgreed,
                email: lockedEmail ? undefined : getValues('email'),
                nickname: getValues('nickname'),
              })
              return clearSignupDraft
            }}
            onReturn={() => {
              const draft = takeSignupDraft()
              if (!draft) return
              restoreDraft(draft)
              setTermsOpen(!draft.termsAgreed)
              if (!lockedEmail && draft.email) setValue('email', draft.email)
              if (draft.nickname) setValue('nickname', draft.nickname, { shouldValidate: true })
            }}
          />
          <Divider type="section" className="-mx-4" />
          <StepIntro title="회원 정보" />
          <LockedIdentityFields identity={verification} provider={{ name, label: providerLabel }} />
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
            gender={gender}
            onGenderChange={(value) => setValue('gender', value, { shouldValidate: true })}
          />
          {formError && (
            <p role="alert" className="text-caption-regular text-text-danger">
              {formError}
            </p>
          )}
        </CenterBody>
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            <Button
              type="submit"
              size="l"
              disabled={!isValid || !verification}
              loading={submitting}
            >
              가입 완료
            </Button>
          }
        />
      </form>
      <SignupTermsSheet open={termsOpen} onOpenChange={setTermsOpen} />
      <DuplicateIdentityDialog open={duplicate} onOpenChange={setDuplicate} />
      <RejoinTooSoonDialog rejoin={rejoin} onClose={() => setRejoin(null)} />
    </>
  )
}
