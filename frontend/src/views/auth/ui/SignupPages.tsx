'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { useForm, useWatch, type FieldErrors, type UseFormRegister } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
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
  Stepper,
  TextField,
  useToast,
} from '@/shared/ui'

import { joinErrorMessage, toJoinRequest } from '../model/join'
import {
  AUTH_ERRORS,
  signupAccountSchema,
  signupProfileSchema,
  type SignupAccountValues,
  type SignupProfileValues,
} from '../model/schemas'
import { saveSignupDraft, takeSignupDraft } from '../model/signup-draft'
import { saveSignupNext, takeSignupNext } from '../model/signup-next'
import { useSignupStep } from '../model/signup-store'
import { requiredTermsChecked, SIGNUP_TERMS } from '../model/signup-terms'
import { CenterBody, StepIntro } from './parts'

const STEPS = ['약관', '본인 확인', '계정', '회원 정보']

// 시안 M04-P 1 · 이메일 가입 본인 확인. 가입의 첫 화면이고, 약관 동의(M04-0) 바텀시트를 이 화면 위에 띄운다.
// 비밀번호를 넣기 전에 본인인증을 받아, 모바일에서 포트원 창을 다녀와도 다시 받을 비밀번호가 없다.
// 인증을 마치면 계정(이메일 · 비밀번호) 단계로 넘어간다. 이미 인증했으면 확인된 정보를 보여 주고 「다음」으로 넘어간다.
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
      <AppHeader type="sub" title="가입하기" />
      {termsAgreed && (
        <div className="bg-bg-surface px-4 pt-3 pb-1">
          <Stepper steps={STEPS} current={1} />
        </div>
      )}
      <CenterBody center={false}>
        <StepIntro
          title="본인 확인"
          description="본인인증을 하면 이름·생년월일·휴대폰 번호가 채워져요. 다음 단계에서 이메일과 비밀번호를 정해요."
        />
        {mock && <IdentityMockNotice />}
        {verification && <LockedIdentityFields identity={verification} />}
      </CenterBody>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Button size="l" loading={opening} onClick={onPrimary}>
            {verification ? '다음' : '휴대폰 본인인증'}
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

// 피그마 V2 · M04-1 계정(이메일 · 비밀번호) (+ M04-1E 입력 오류). 약관 동의와 본인인증을 마친 뒤 온다.
// invalid는 시안 미리보기(?preview=error)용. 앞 단계를 건너뛰고 M04-1E 오류를 띄운 채로 보여 준다.
// 입력값은 SignupStepProvider((auth) 레이아웃)에 남아 회원 정보 단계에서 돌아와도 그대로다.
export function SignupAccountPage({ invalid = false }: { invalid?: boolean }) {
  const router = useRouter()
  const termsAgreed = useSignupStep((s) => s.termsAgreed)
  const verification = useSignupStep((s) => s.verification)
  const ready = invalid || (termsAgreed && verification !== null)
  useEffect(() => {
    if (!ready) router.replace('/signup')
  }, [ready, router])

  return ready ? <SignupAccountForm invalid={invalid} /> : null
}

function SignupAccountForm({ invalid }: { invalid: boolean }) {
  const router = useRouter()
  const account = useSignupStep((s) => s.account)
  const setAccount = useSignupStep((s) => s.setAccount)

  const {
    register,
    handleSubmit,
    subscribe,
    getValues,
    trigger,
    setError,
    formState: { errors, isValid, isSubmitting },
  } = useForm<SignupAccountValues>({
    resolver: zodResolver(signupAccountSchema),
    mode: 'onTouched',
    defaultValues: invalid
      ? { email: 'runner@crewrun.kr', password: 'password12', passwordConfirm: 'password13' }
      : account,
  })
  // 입력할 때마다 스토어에 옮겨 둔다. 뒤로 왔다가 다시 와도 값이 남는다.
  useEffect(() => {
    if (invalid) return
    return subscribe({
      formState: { values: true },
      callback: ({ values }) => setAccount(values),
    })
  }, [invalid, subscribe, setAccount])
  // 비밀번호를 고치면 이미 채운 확인 칸도 다시 검사한다. 비어 있으면 "다르다" 오류를 먼저 띄우지 않는다.
  const revalidateConfirm = () => {
    if (getValues('passwordConfirm')) void trigger('passwordConfirm')
  }
  // 이메일 중복 확인. 칸을 벗어날 때 먼저 알려 주고, "다음"에서도 한 번 더 확인해 중복이면 못 넘어가게 한다.
  // quiet: 칸을 벗어날 때처럼 미리 알려 주는 확인. 형식이 맞는 이메일만 묻고, 서버에 닿지 못해도 오류를 띄우지 않는다.
  // "다음"에서는 확인 요청이 실패해도 칸 오류로 알리고 넘어가지 않는다.
  const [emailCheckFailed, setEmailCheckFailed] = useState(false)
  const checkEmail = async (quiet = false): Promise<boolean> => {
    setEmailCheckFailed(false)
    const email = getValues('email').trim()
    if (quiet && !signupAccountSchema.shape.email.safeParse(email).success) return false
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
    if (await checkEmail()) router.push('/signup/profile')
  })

  return (
    <>
      <AppHeader type="sub" title="가입하기" />
      <form noValidate onSubmit={onFormSubmit} className="contents">
        <div className="bg-bg-surface px-4 pt-3 pb-1">
          <Stepper steps={STEPS} current={2} />
        </div>
        <CenterBody center={false}>
          <StepIntro
            title="계정을 만들어요"
            description="이메일과 비밀번호를 정해요. 다음은 회원 정보예요."
          />
          <TextField
            label="이메일"
            type="email"
            autoComplete="email"
            placeholder="이메일을 입력해 주세요"
            error={
              errors.email?.message ??
              (emailCheckFailed ? AUTH_ERRORS.emailCheckFailed : undefined) ??
              (invalid ? AUTH_ERRORS.emailTaken : undefined)
            }
            {...register('email', { onBlur: () => void checkEmail(true) })}
          />
          <TextField
            label="비밀번호"
            type="password"
            autoComplete="new-password"
            placeholder="비밀번호를 입력해 주세요"
            helper="10자 이상"
            error={errors.password?.message}
            {...register('password', { onChange: revalidateConfirm })}
          />
          <TextField
            label="비밀번호 확인"
            type="password"
            autoComplete="new-password"
            placeholder="비밀번호를 한 번 더 입력해 주세요"
            error={
              errors.passwordConfirm?.message ??
              (invalid ? AUTH_ERRORS.passwordMismatch : undefined)
            }
            {...register('passwordConfirm')}
          />
        </CenterBody>
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            <Button type="submit" size="l" disabled={!isValid || invalid} loading={isSubmitting}>
              다음
            </Button>
          }
        />
      </form>
    </>
  )
}

// 시안 M04-P 4 · 이메일 가입 회원 정보. 이름 · 생년월일 · 휴대폰 번호는 본인인증 결과로 잠겨 있고, 닉네임과 성별만 적는다.
// 앞 단계(약관 · 본인인증 · 계정)를 마치지 않고 들어오면(주소 직접 입력, 새로고침) 첫 단계로 돌려보낸다.
export function SignupProfilePage() {
  const router = useRouter()
  const termsAgreed = useSignupStep((s) => s.termsAgreed)
  const account = useSignupStep((s) => s.account)
  const verification = useSignupStep((s) => s.verification)
  const ready =
    termsAgreed && verification !== null && signupAccountSchema.safeParse(account).success
  useEffect(() => {
    if (!ready) router.replace('/signup')
  }, [ready, router])

  return ready ? <SignupProfileForm /> : null
}

function SignupProfileForm() {
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
  const gender = useWatch({ control, name: 'gender' })
  useEffect(
    () =>
      subscribe({
        formState: { values: true },
        callback: ({ values }) => setProfile(values),
      }),
    [subscribe, setProfile],
  )
  // 가입(POST /api/v1/auth/join) 뒤 같은 이메일 · 비밀번호로 바로 로그인해 홈으로 간다.
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
              onSuccess: () => router.replace(next),
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
        <AppHeader type="sub" title="가입하기" />
        <div className="bg-bg-surface px-4 pt-3 pb-1">
          <Stepper steps={STEPS} current={3} />
        </div>
        <CenterBody center={false}>
          <StepIntro title="회원 정보" description="마지막 단계예요. 다음은 가입 완료예요." />
          <LockedIdentityFields identity={verification} />
          <SignupProfileFields
            register={register}
            errors={errors}
            gender={gender}
            onGenderChange={(gender) => setValue('gender', gender, { shouldValidate: true })}
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
            <Button type="submit" size="l" disabled={!isValid} loading={submitting}>
              가입 완료
            </Button>
          }
        />
      </form>
      <DuplicateIdentityDialog open={duplicate} onOpenChange={setDuplicate} />
      <RejoinTooSoonDialog rejoin={rejoin} onClose={() => setRejoin(null)} />
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
          동의하고 계속하기
        </Button>
      }
    >
      <div className="flex flex-col gap-3 px-2 pt-1 pb-3">
        <p className="text-body-m text-text-secondary">
          대회 신청과 거래를 위해 꼭 필요한 약관이에요.
        </p>
        <div className="rounded-md bg-bg-subtle p-4">
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
                  href={term.href}
                  aria-label={`${term.label} 보기`}
                  className={cn('rounded-sm text-text-primary', FOCUS_RING)}
                >
                  <Icon name="ChevronRight" className="size-5" />
                </Link>
              )}
            </div>
          ))}
        </div>
        <p className="text-caption text-text-tertiary">
          마케팅 정보 동의는 2년마다 다시 여쭤봐요. 마이에서 언제든 끌 수 있어요.
        </p>
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
        <legend className="mb-2 text-label-strong text-text-primary">성별 (선택)</legend>
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
      body="로그인하거나 계정을 연결해 주세요. 계정 연결은 로그인한 뒤 마이 › 계정 관리에서 해요."
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
