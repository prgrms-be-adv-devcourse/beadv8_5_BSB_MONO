'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState, type FormEvent } from 'react'
import { useForm, type FieldErrors, type UseFormRegister } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import { checkEmailAvailable, useLogin } from '@/features/auth'
import { BirthDateField } from '@/entities/member'
import { memberApi } from '@/shared/api'
import { cn } from '@/shared/lib'
import {
  BottomCta,
  BottomSheet,
  Button,
  Checkbox,
  FOCUS_RING,
  Icon,
  PhoneField,
  Stepper,
  TextField,
} from '@/shared/ui'

import { joinErrorMessage, toJoinRequest } from '../model/join'
import {
  AUTH_ERRORS,
  signupAccountSchema,
  signupProfileSchema,
  type SignupAccountValues,
  type SignupProfileValues,
} from '../model/schemas'
import { useSignupStep } from '../model/signup-store'
import { requiredTermsChecked, SIGNUP_TERMS } from '../model/signup-terms'
import { CenterBody, StepIntro } from './parts'

const STEPS = ['약관', '계정', '회원 정보']

// 피그마 V2 · M04-0 약관 동의(바텀시트) → M04-1 계정(이메일 · 비밀번호) (+ M04-1E 입력 오류)
// invalid는 시안 미리보기(?preview=error)용. 약관을 건너뛰고 M04-1E 오류를 띄운 채로 보여 준다.
// 입력값과 약관 체크는 SignupStepProvider((auth) 레이아웃)에 남아 회원 정보 단계에서 돌아와도 그대로다.
export function SignupAccountPage({ invalid = false }: { invalid?: boolean }) {
  const router = useRouter()
  const termsAgreed = useSignupStep((s) => s.termsAgreed)
  const account = useSignupStep((s) => s.account)
  const setAccount = useSignupStep((s) => s.setAccount)

  const [termsOpen, setTermsOpen] = useState(!invalid && !termsAgreed)
  const agreed = invalid || termsAgreed

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
  const onNext = handleSubmit(async () => {
    if (await checkEmail()) router.push('/signup/profile')
  })
  // 약관 시트를 동의 없이 닫았다면 "다음"이 시트를 다시 연다. 동의해야 다음 단계로 간다.
  // 입력 칸에서 Enter를 눌러도 같은 길로 오도록 form 제출에 묶는다. 버튼이 꺼져 있으면 Enter로도 제출되지 않는다.
  const onFormSubmit = (event: FormEvent<HTMLFormElement>) => {
    if (termsAgreed || invalid) return onNext(event)
    event.preventDefault()
    setTermsOpen(true)
  }

  return (
    <>
      <AppHeader type="sub" title="가입하기" />
      <form noValidate onSubmit={onFormSubmit} className="contents">
        {agreed && (
          <div className="bg-bg-surface px-4 pt-3 pb-1">
            <Stepper steps={STEPS} current={1} />
          </div>
        )}
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
      <SignupTermsSheet open={termsOpen} onOpenChange={setTermsOpen} />
    </>
  )
}

// 피그마 V2 · M04-2 가입 · 회원 정보
// 약관 동의와 계정 입력을 마치지 않고 들어오면(주소 직접 입력, 새로고침) 첫 단계로 돌려보낸다.
export function SignupProfilePage() {
  const router = useRouter()
  const termsAgreed = useSignupStep((s) => s.termsAgreed)
  const account = useSignupStep((s) => s.account)
  const ready = termsAgreed && signupAccountSchema.safeParse(account).success
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
  const join = memberApi.useJoin()
  const login = useLogin()
  const [formError, setFormError] = useState<string>()
  const {
    register,
    handleSubmit,
    subscribe,
    setError,
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
  // 가입(POST /api/v1/auth/join) 뒤 같은 이메일 · 비밀번호로 바로 로그인해 홈으로 간다.
  // 가입 API는 토큰을 주지 않는다. 로그인만 실패하면 가입은 됐으므로 로그인 화면으로 보낸다.
  const onSubmit = handleSubmit((values) => {
    setFormError(undefined)
    const body = toJoinRequest(account, values, terms)
    join.mutate(
      { data: body },
      {
        onSuccess: () =>
          login.mutate(
            { email: body.email, password: body.password },
            {
              onSuccess: () => router.replace('/'),
              onError: () => router.replace('/login'),
            },
          ),
        onError: (error) => {
          const { target, message } = joinErrorMessage(error)
          if (target === 'form') setFormError(message)
          else setError(target, { message }, { shouldFocus: true })
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
    <form noValidate onSubmit={onSubmit} className="contents">
      <AppHeader type="sub" title="가입하기" />
      <div className="bg-bg-surface px-4 pt-3 pb-1">
        <Stepper steps={STEPS} current={2} />
      </div>
      <CenterBody center={false}>
        <StepIntro
          title="회원 정보를 적어요"
          description="마지막 단계예요. 다음은 가입 완료예요."
        />
        <SignupProfileFields register={register} errors={errors} />
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

/** 회원 정보 칸(이름 · 생년월일 · 휴대폰 · 닉네임). 이메일 가입과 소셜 가입이 함께 쓴다. */
export function SignupProfileFields({
  register,
  errors,
}: {
  register: UseFormRegister<SignupProfileValues>
  errors: FieldErrors<SignupProfileValues>
}) {
  return (
    <>
      <TextField
        label="이름"
        autoComplete="name"
        error={errors.name?.message}
        {...register('name')}
      />
      <BirthDateField
        label="생년월일"
        helper="만 14세 미만은 가입할 수 없어요"
        error={errors.birth?.message}
        {...register('birth')}
      />
      <PhoneField
        label="휴대폰 번호"
        placeholder="010-1234-5678"
        error={errors.phone?.message}
        {...register('phone')}
      />
      <TextField
        label="닉네임"
        helper="다른 사람에게는 실명 대신 닉네임이 보여요. 한글 2~8자, 영문 4~16자"
        error={errors.nickname?.message}
        {...register('nickname')}
      />
      <p className="text-caption text-text-tertiary">
        이름 · 생년월일 · 휴대폰 번호는 대회 신청 때 참가자 정보로 그대로 가져와요.
      </p>
    </>
  )
}
