'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import type { Route } from 'next'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { useForm, useWatch, type FieldErrors, type UseFormRegister } from 'react-hook-form'

import { checkEmailAvailable, loginHref, safeNextPath, useLogin } from '@/features/auth'
import {
  IdentityMockNotice,
  LockedIdentityFields,
  rejoinInfoOf,
  rejoinMessage,
  useIdentityVerification,
  type RejoinInfo,
} from '@/features/identity-verification'
import { memberApi } from '@/shared/api'
import { cn } from '@/shared/lib'
import {
  BottomCta,
  BottomSheet,
  Button,
  Checkbox,
  Dialog,
  FOCUS_RING,
  Icon,
  Radio,
  RadioGroup,
  TextField,
  useToast,
} from '@/shared/ui'

import { joinErrorMessage, toJoinRequest } from '../model/join'
import {
  AUTH_ERRORS,
  PASSWORD_MAX,
  signupAccountSchema,
  signupEmailSchema,
  signupPasswordSchema,
  signupProfileSchema,
  type SignupProfileValues,
} from '../model/schemas'
import { saveSignupDraft, takeSignupDraft } from '../model/signup-draft'
import { saveSignupNext, signupStartHref, takeSignupNext } from '../model/signup-next'
import { useSignupStep } from '../model/signup-store'
import { requiredTermsChecked, SIGNUP_TERMS } from '../model/signup-terms'
import { CenterBody, LineField, StatusScreen, StepHeader, StepIntro } from './parts'

// 시안 가입 · 본인 확인. 가입의 첫 화면이고, 약관 동의(M04-0) 바텀시트를 이 화면 위에 띄운다.
// 비밀번호를 넣기 전에 본인인증을 받아, 모바일에서 포트원 창을 다녀와도 다시 받을 비밀번호가 없다.
// 인증을 마치면 이메일 · 비밀번호 단계로 넘어간다. 이미 인증했으면 확인된 정보를 보여 주고 「다음」으로 넘어간다.
// next는 가입을 마친 뒤 돌아갈 화면(?next=, BUK-98). 마지막 단계까지 남겨 둔다.
// next 없이 들어오면 전에 그만둔 가입이 남긴 값을 지운다. 모바일 본인인증은 지금 주소(?next= 포함)로 돌아오므로 지워도 된다.
export function SignupIdentityPage({ next }: { next?: string } = {}) {
  const router = useRouter()
  useEffect(() => {
    if (next) saveSignupNext(next)
    else takeSignupNext()
  }, [next])
  const terms = useSignupStep((s) => s.terms)
  const termsAgreed = useSignupStep((s) => s.termsAgreed)
  const verification = useSignupStep((s) => s.verification)
  const setVerification = useSignupStep((s) => s.setVerification)
  const restoreDraft = useSignupStep((s) => s.restoreDraft)
  const [termsOpen, setTermsOpen] = useState(!termsAgreed)
  const [duplicate, setDuplicate] = useState(false)
  const [rejoin, setRejoin] = useState<RejoinInfo | null>(null)

  const { start, opening, mock, element } = useIdentityVerification({
    onDuplicate: () => setDuplicate(true),
    onRejoinTooSoon: setRejoin,
    onVerified: (identity) => {
      setVerification(identity)
      router.push('/signup/account')
    },
    onBeforeOpen: () => saveSignupDraft({ terms, termsAgreed }),
    onReturn: () => {
      const draft = takeSignupDraft()
      if (draft) {
        restoreDraft(draft)
        setTermsOpen(!draft.termsAgreed)
      }
    },
  })
  // 약관 시트를 동의 없이 닫았다면 버튼이 시트를 다시 연다. 동의해야 본인인증을 시작한다.
  const onPrimary = () => {
    if (!termsAgreed) return setTermsOpen(true)
    if (verification) return router.push('/signup/account')
    start()
  }

  return (
    <>
      <StepHeader onBack={() => router.back()} />
      <CenterBody center={false}>
        <StepIntro title={'휴대폰으로\n본인 확인을 해 주세요'} />
        {mock && <IdentityMockNotice />}
        {verification && <LockedIdentityFields identity={verification} />}
      </CenterBody>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Button size="l" loading={opening} onClick={onPrimary}>
            {verification ? '다음' : '본인 확인하기'}
          </Button>
        }
      />
      {element}
      <SignupTermsSheet open={termsOpen} onOpenChange={setTermsOpen} />
      <DuplicateIdentityDialog open={duplicate} onOpenChange={setDuplicate} />
      <RejoinTooSoonDialog rejoin={rejoin} onClose={() => setRejoin(null)} />
    </>
  )
}

// 시안 가입 · 이메일 → 비밀번호 (두 화면). 약관 동의와 본인인증을 마친 뒤 온다. 한 주소(/signup/account) 안에서 단계만 바뀐다.
// invalid는 시안 미리보기(?preview=error)용. 앞 단계를 건너뛰고 「비밀번호 · 너무 김」 오류를 띄운 채로 보여 준다.
// 입력값은 SignupStepProvider((auth) 레이아웃)에 남아 닉네임 단계에서 돌아와도 그대로다.
export function SignupAccountPage({ invalid = false }: { invalid?: boolean }) {
  const router = useRouter()
  const termsAgreed = useSignupStep((s) => s.termsAgreed)
  const verification = useSignupStep((s) => s.verification)
  const ready = invalid || (termsAgreed && verification !== null)
  const [step, setStep] = useState<'email' | 'password'>(invalid ? 'password' : 'email')
  useEffect(() => {
    if (!ready) router.replace(signupStartHref())
  }, [ready, router])

  if (!ready) return null
  return step === 'email' ? (
    <SignupEmailStep onNext={() => setStep('password')} />
  ) : (
    <SignupPasswordStep invalid={invalid} onBack={() => setStep('email')} />
  )
}

// 이메일 단계. 뒤로 가면 「가입을 그만둘까요?」를 먼저 묻는다. 그만두면 적은 내용과 본인인증 결과를 지우고 로그인 화면으로 간다.
function SignupEmailStep({ onNext }: { onNext: () => void }) {
  const router = useRouter()
  const account = useSignupStep((s) => s.account)
  const setAccount = useSignupStep((s) => s.setAccount)
  const reset = useSignupStep((s) => s.reset)
  const [quitOpen, setQuitOpen] = useState(false)
  const {
    register,
    handleSubmit,
    subscribe,
    getValues,
    setError,
    formState: { errors, isValid, isSubmitting },
  } = useForm<{ email: string }>({
    resolver: zodResolver(signupEmailSchema),
    mode: 'onTouched',
    defaultValues: { email: account.email },
  })
  // 입력할 때마다 스토어에 옮겨 둔다. 뒤로 왔다가 다시 와도 값이 남는다.
  useEffect(
    () =>
      subscribe({
        formState: { values: true },
        callback: ({ values }) => setAccount({ email: values.email }),
      }),
    [subscribe, setAccount],
  )
  // 이메일 중복 확인. 칸을 벗어날 때 먼저 알려 주고, "다음"에서도 한 번 더 확인해 중복이면 못 넘어가게 한다.
  // quiet: 칸을 벗어날 때처럼 미리 알려 주는 확인. 형식이 맞는 이메일만 묻고, 서버에 닿지 못해도 오류를 띄우지 않는다.
  // "다음"에서는 확인 요청이 실패해도 칸 오류로 알리고 넘어가지 않는다.
  const [emailCheckFailed, setEmailCheckFailed] = useState(false)
  const checkEmail = async (quiet = false): Promise<boolean> => {
    setEmailCheckFailed(false)
    const email = getValues('email').trim()
    if (quiet && !signupEmailSchema.shape.email.safeParse(email).success) return false
    try {
      const available = await checkEmailAvailable(email)
      if (!available && getValues('email').trim() === email) {
        setError('email', { type: 'taken', message: AUTH_ERRORS.emailTaken })
      }
      return available
    } catch {
      // setError는 폼을 "유효하지 않음"으로 만들어 버튼이 막히므로, 확인 실패는 따로 들고 있는다.
      if (!quiet) setEmailCheckFailed(true)
      return false
    }
  }
  // 입력 칸에서 Enter를 눌러도 같은 길로 오도록 form 제출에 묶는다. 버튼이 꺼져 있으면 Enter로도 제출되지 않는다.
  const onFormSubmit = handleSubmit(async () => {
    if (await checkEmail()) onNext()
  })

  return (
    <>
      <StepHeader onBack={() => setQuitOpen(true)} />
      <form noValidate onSubmit={onFormSubmit} className="contents">
        <CenterBody center={false}>
          <StepIntro title={'로그인할 때 쓸\n이메일을 적어 주세요'} />
          <LineField
            aria-label="이메일"
            type="email"
            autoComplete="email"
            autoFocus
            error={
              errors.email?.message ?? (emailCheckFailed ? AUTH_ERRORS.emailCheckFailed : undefined)
            }
            {...register('email', { onBlur: () => void checkEmail(true) })}
          />
        </CenterBody>
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            <Button type="submit" size="l" disabled={!isValid} loading={isSubmitting}>
              다음
            </Button>
          }
        />
      </form>
      <Dialog
        open={quitOpen}
        onOpenChange={setQuitOpen}
        title="가입을 그만둘까요?"
        body="지금 나가면 적은 내용이 지워져요. 다음에 가입할 때 본인 확인부터 다시 해야 해요."
        cancelLabel="그만두기"
        confirmLabel="계속 가입하기"
        onCancel={() => {
          takeSignupNext()
          reset()
          router.replace('/login')
        }}
        onConfirm={() => setQuitOpen(false)}
      />
    </>
  )
}

// 비밀번호 단계. 한 번만 적고, 조건(10자 이상)을 맞추면 아래에 초록 체크가 뜬다.
function SignupPasswordStep({ invalid, onBack }: { invalid: boolean; onBack: () => void }) {
  const router = useRouter()
  const password = useSignupStep((s) => s.account.password)
  const setAccount = useSignupStep((s) => s.setAccount)
  const {
    register,
    handleSubmit,
    subscribe,
    control,
    formState: { isValid },
  } = useForm<{ password: string }>({
    resolver: zodResolver(signupPasswordSchema),
    mode: 'onChange',
    defaultValues: { password: invalid ? 'password1234'.repeat(6) : password },
  })
  useEffect(() => {
    if (invalid) return
    return subscribe({
      formState: { values: true },
      callback: ({ values }) => setAccount({ password: values.password }),
    })
  }, [invalid, subscribe, setAccount])
  const typed = useWatch({ control, name: 'password' })

  return (
    <>
      <StepHeader onBack={onBack} />
      <form
        noValidate
        onSubmit={handleSubmit(() => router.push('/signup/profile'))}
        className="contents"
      >
        <CenterBody center={false}>
          <StepIntro title={'비밀번호를\n정해 주세요'} />
          <LineField
            aria-label="비밀번호"
            type="password"
            autoComplete="new-password"
            autoFocus
            error={typed.length > PASSWORD_MAX ? AUTH_ERRORS.passwordTooLong : undefined}
            hints={[{ ok: typed.length >= 10, text: '10자 이상이에요' }]}
            {...register('password')}
          />
        </CenterBody>
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            <Button type="submit" size="l" disabled={!isValid}>
              다음
            </Button>
          }
        />
      </form>
    </>
  )
}

// 시안 가입 · 닉네임 → 가입 완료. 본인인증 결과와 이메일 · 비밀번호는 앞 단계에서 받았으므로 닉네임만 적는다.
// 앞 단계(약관 · 본인인증 · 이메일 · 비밀번호)를 마치지 않고 들어오면(주소 직접 입력, 새로고침) 첫 단계로 돌려보낸다.
export function SignupProfilePage() {
  const router = useRouter()
  const termsAgreed = useSignupStep((s) => s.termsAgreed)
  const account = useSignupStep((s) => s.account)
  const verification = useSignupStep((s) => s.verification)
  const ready =
    termsAgreed && verification !== null && signupAccountSchema.safeParse(account).success
  // 가입과 로그인을 마치면 돌아갈 화면(next)을 담아 완료 화면으로 바꾼다
  const [done, setDone] = useState<Route>()
  useEffect(() => {
    if (!ready) router.replace(signupStartHref())
  }, [ready, router])

  if (done !== undefined) return <SignupDonePage next={done} />
  return ready ? <SignupProfileForm onDone={setDone} /> : null
}

function SignupProfileForm({ onDone }: { onDone: (next: Route) => void }) {
  const router = useRouter()
  const account = useSignupStep((s) => s.account)
  const terms = useSignupStep((s) => s.terms)
  const profile = useSignupStep((s) => s.profile)
  const setProfile = useSignupStep((s) => s.setProfile)
  const verification = useSignupStep((s) => s.verification)
  const setVerification = useSignupStep((s) => s.setVerification)
  const join = memberApi.useJoin()
  const login = useLogin()
  const toast = useToast()
  const [formError, setFormError] = useState<string>()
  const [duplicate, setDuplicate] = useState(false)
  const [rejoin, setRejoin] = useState<RejoinInfo | null>(null)
  const {
    register,
    handleSubmit,
    subscribe,
    setError,
    setValue,
    control,
    formState: { errors, isValid, isSubmitting },
  } = useForm<SignupProfileValues>({
    resolver: zodResolver(signupProfileSchema),
    mode: 'onTouched',
    defaultValues: profile,
  })
  useEffect(
    () =>
      subscribe({
        formState: { values: true },
        callback: ({ values }) => setProfile(values),
      }),
    [subscribe, setProfile],
  )
  const nickname = useWatch({ control, name: 'nickname' })
  // 가입(POST /api/v1/auth/join) 뒤 같은 이메일 · 비밀번호로 바로 로그인하고 「가입을 마쳤어요」를 보여 준다.
  // 가입 API는 토큰을 주지 않는다. 로그인만 실패하면 가입은 됐으므로 로그인 화면으로 보낸다.
  const onSubmit = handleSubmit((values) => {
    setFormError(undefined)
    if (!verification) return
    const body = toJoinRequest(account, values, terms, verification.verificationToken)
    join.mutate(
      { data: body },
      {
        onSuccess: () => {
          // 가입이 실패해 다시 시도해도 남아 있도록, 가입이 된 뒤에 꺼낸다
          const next = safeNextPath(takeSignupNext())
          login.mutate(
            { email: body.email, password: body.password },
            {
              onSuccess: () => onDone(next),
              onError: () => router.replace(next === '/' ? '/login' : loginHref(next)),
            },
          )
        },
        onError: (error) => {
          const { target, message } = joinErrorMessage(error)
          if (target === 'duplicate') return setDuplicate(true)
          if (target === 'rejoinTooSoon') return setRejoin(rejoinInfoOf(error))
          if (target === 'nickname') return setError(target, { message }, { shouldFocus: true })
          // 본인인증 결과가 지났으면 인증 결과를 지운다. 그러면 첫 단계(본인 확인)로 돌아가므로 안내는 Toast로 띄운다
          if (target === 'verification') {
            toast({ message, tone: 'error' })
            setVerification(null)
            return
          }
          setFormError(message)
        },
      },
    )
  })
  // 가입 → 자동 로그인 두 요청이 이어져 몇 초 걸릴 수 있다. 버튼에 Spinner를 띄워 멈춘 것처럼 보이지 않게 한다.
  // loading 버튼은 클릭을 막으므로 Enter로도 다시 제출되지 않는다.
  // 가입이 끝나면 로그인 결과와 상관없이 다른 화면으로 가므로, 이동할 때까지 계속 막아 두 번 가입하지 않게 한다.
  // isSubmitting은 handleSubmit이 입력 검사를 하는 동안을 덮는다. 그 사이 Enter · 더블클릭으로 두 번 제출되지 않게 한다.
  const submitting = isSubmitting || join.isPending || join.isSuccess

  return (
    <>
      <form noValidate onSubmit={onSubmit} className="contents">
        <StepHeader onBack={() => router.back()} />
        <CenterBody center={false}>
          <StepIntro title={'크루런에서 쓸\n닉네임을 정해 주세요'} />
          <LineField
            aria-label="닉네임"
            autoComplete="nickname"
            autoFocus
            error={errors.nickname?.message ?? formError}
            hints={[
              nickname && isValid
                ? { ok: true, text: '규칙에 맞아요' }
                : { ok: false, text: '한글 2~8자, 영문 4~16자' },
            ]}
            onClear={
              nickname ? () => setValue('nickname', '', { shouldValidate: true }) : undefined
            }
            {...register('nickname')}
          />
        </CenterBody>
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            <Button type="submit" size="l" disabled={!isValid} loading={submitting}>
              가입 완료하기
            </Button>
          }
        />
      </form>
      <DuplicateIdentityDialog open={duplicate} onOpenChange={setDuplicate} />
      <RejoinTooSoonDialog rejoin={rejoin} onClose={() => setRejoin(null)} />
    </>
  )
}

// 시안 가입 완료. 가입과 자동 로그인을 마치면 이 화면을 보여 주고, 「홈으로 가기」가 next(없으면 홈)로 보낸다.
function SignupDonePage({ next }: { next: Route }) {
  const router = useRouter()
  return (
    <>
      <StepHeader title="가입하기" />
      <StatusScreen title="가입을 마쳤어요" />
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Button size="l" onClick={() => router.replace(next)}>
            홈으로 가기
          </Button>
        }
      />
    </>
  )
}

/**
 * 약관 동의 바텀시트(M04-0). 이메일 가입과 소셜 가입이 함께 쓴다.
 * 체크와 동의 여부는 SignupStepProvider((auth) 레이아웃)에 남아, 약관 전문을 보고 돌아와도 그대로다.
 */
export function SignupTermsSheet({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const terms = useSignupStep((s) => s.terms)
  const setTerm = useSignupStep((s) => s.setTerm)
  const setAllTerms = useSignupStep((s) => s.setAllTerms)
  const agreeTerms = useSignupStep((s) => s.agreeTerms)
  const allChecked = SIGNUP_TERMS.every((t) => terms[t.id])
  const requiredChecked = requiredTermsChecked(terms)
  return (
    <BottomSheet
      open={open}
      onOpenChange={onOpenChange}
      title="약관에 동의해 주세요"
      footer={
        <Button
          size="l"
          disabled={!requiredChecked}
          onClick={() => {
            if (agreeTerms()) onOpenChange(false)
          }}
        >
          동의하고 본인 확인하기
        </Button>
      }
    >
      <div className="flex flex-col gap-3 px-2 pt-1 pb-3">
        <div className="rounded-md border border-border-default p-3">
          <Checkbox
            checked={allChecked}
            onCheckedChange={setAllTerms}
            label={<span className="font-bold">모두 동의해요 (선택 포함)</span>}
          />
        </div>
        <div className="flex flex-col px-1">
          {SIGNUP_TERMS.map((term) => (
            <div key={term.id} className="flex min-h-11 items-center justify-between py-3">
              <Checkbox
                checked={terms[term.id]}
                onCheckedChange={(value) => setTerm(term.id, value)}
                label={term.label}
              />
              {'href' in term && (
                <Link
                  href={{ pathname: term.href, query: { from: 'signup' } }}
                  aria-label={`${term.label} 보기`}
                  className={cn('rounded-sm text-text-primary', FOCUS_RING)}
                >
                  <Icon name="ChevronRight" className="size-5" />
                </Link>
              )}
            </div>
          ))}
        </div>
      </div>
    </BottomSheet>
  )
}

const GENDERS: { value: SignupProfileValues['gender']; label: string }[] = [
  { value: 'FEMALE', label: '여성' },
  { value: 'MALE', label: '남성' },
  { value: '', label: '선택 안 함' },
]

/** 회원 정보 칸(닉네임 · 성별). 이메일 가입과 소셜 가입이 함께 쓴다. 이름 · 생년월일 · 휴대폰 번호는 본인인증으로 받는다 */
export function SignupProfileFields({
  register,
  errors,
  gender,
  onGenderChange,
}: {
  register: UseFormRegister<SignupProfileValues>
  errors: FieldErrors<SignupProfileValues>
  gender: SignupProfileValues['gender']
  onGenderChange: (gender: SignupProfileValues['gender']) => void
}) {
  return (
    <>
      <TextField
        label="닉네임"
        helper="다른 사람에게는 실명 대신 닉네임이 보여요. 한글 2~8자, 영문 4~16자"
        error={errors.nickname?.message}
        {...register('nickname')}
      />
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-body2-bold text-text-primary">성별 (선택)</legend>
        <RadioGroup
          value={gender}
          onValueChange={(value) => onGenderChange(value as SignupProfileValues['gender'])}
          className="flex flex-row gap-5"
        >
          {GENDERS.map((item) => (
            <Radio key={item.label} value={item.value} label={item.label} />
          ))}
        </RadioGroup>
      </fieldset>
    </>
  )
}

/** 시안 M04-P 3 · 이미 가입한 사람. 같은 사람(본인인증)이나 같은 휴대폰 번호로 이미 가입했으면 로그인을 안내한다 */
export function DuplicateIdentityDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const router = useRouter()
  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title="이미 가입한 사람이에요"
      body="로그인하거나 계정을 연결해 주세요."
      cancelLabel="닫기"
      confirmLabel="로그인하기"
      onConfirm={() => router.push('/login')}
    />
  )
}

/** 탈퇴하고 30일이 지나지 않은 사람에게 다시 가입할 수 있는 날을 알려 준다 (11/2 결정, 시안 없어 「이미 가입한 사람이에요」와 같은 모양) */
export function RejoinTooSoonDialog({
  rejoin,
  onClose,
}: {
  rejoin: RejoinInfo | null
  onClose: () => void
}) {
  return (
    <Dialog
      open={rejoin !== null}
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
      type="alert"
      title="아직 다시 가입할 수 없어요"
      body={rejoin ? rejoinMessage(rejoin) : undefined}
      confirmLabel="확인"
      onConfirm={onClose}
    />
  )
}
