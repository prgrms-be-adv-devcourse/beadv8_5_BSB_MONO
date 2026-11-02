'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useRef } from 'react'
import { useForm } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import { BirthDateField } from '@/entities/member'
import { ApiError, clearAccessToken, memberApi } from '@/shared/api'
import {
  Alert,
  BottomCta,
  Button,
  buttonClassName,
  EmptyState,
  PhoneField,
  Spinner,
  TextField,
  useToast,
} from '@/shared/ui'

import {
  findEmailSchema,
  passwordNewSchema,
  passwordResetSchema,
  type FindEmailValues,
  type PasswordNewValues,
  type PasswordResetValues,
} from '../model/schemas'
import { AuthIntro, CenterBody } from './parts'

// 피그마 V2 · M03-1 비밀번호 재설정 · 이메일
// 가입 여부와 상관없이 같은 안내를 보여 준다(서버도 같은 응답을 준다). 같은 이메일은 1분에 한 번만 받는다(429).
export function PasswordResetPage() {
  const requestReset = memberApi.useRequestPasswordReset()
  const {
    register,
    handleSubmit,
    formState: { errors, isValid },
  } = useForm<PasswordResetValues>({
    resolver: zodResolver(passwordResetSchema),
    mode: 'onTouched',
    defaultValues: { email: '' },
  })
  const onSubmit = handleSubmit((values) => requestReset.mutate({ data: values }))

  if (requestReset.isSuccess) {
    return (
      <>
        <AppHeader type="sub" title="비밀번호 재설정" />
        <main className="flex flex-1 items-center justify-center px-4 py-6">
          <EmptyState
            icon="CircleCheck"
            title="메일을 보냈어요"
            description="입력한 이메일이 가입된 이메일이면 재설정 링크를 보냈어요. 링크는 30분 동안 쓸 수 있어요. 메일이 보이지 않으면 스팸함도 확인해 주세요."
            action={
              <Button variant="secondary" onClick={() => requestReset.reset()}>
                다시 보내기
              </Button>
            }
          />
        </main>
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            <Link href="/login" className={buttonClassName({ size: 'l' })}>
              로그인하기
            </Link>
          }
        />
      </>
    )
  }

  return (
    <>
      <AppHeader type="sub" title="비밀번호 재설정" />
      <CenterBody>
        <AuthIntro title="비밀번호를 잊으셨나요?" />
        {requestReset.isError && (
          <Alert tone="danger" title={requestErrorMessage(requestReset.error)} />
        )}
        <TextField
          label="이메일"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email', { onChange: () => requestReset.isError && requestReset.reset() })}
        />
      </CenterBody>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Button
            size="l"
            disabled={!isValid || requestReset.isPending}
            loading={requestReset.isPending}
            onClick={onSubmit}
          >
            재설정 링크 보내기
          </Button>
        }
      />
    </>
  )
}

function requestErrorMessage(error: unknown) {
  if (error instanceof ApiError && error.code === 'AUTH_RESET_TOO_MANY') {
    return '잠시 뒤 다시 요청해 주세요. 같은 이메일은 1분에 한 번 보낼 수 있어요.'
  }
  return '메일을 보내지 못했어요. 잠시 뒤 다시 시도해 주세요.'
}

// 메일 링크는 /password-reset/new#token=... 이다. # 뒤는 서버로 가지 않아 접속 로그에 남지 않는다.
// 읽은 뒤 주소창에서 지워 화면을 공유하거나 뒤로 가도 토큰이 보이지 않게 한다.
function takeTokenFromHash() {
  const token = new URLSearchParams(window.location.hash.slice(1)).get('token') ?? ''
  if (window.location.hash) {
    window.history.replaceState(null, '', window.location.pathname + window.location.search)
  }
  return token
}

// 피그마 V2 · M03-2 새 비밀번호 정하기 (메일 링크로 들어온다)
// 화면을 열 때 링크가 아직 쓸 수 있는지 먼저 확인한다. 만료 · 이미 사용 · 더 새 링크가 나온 경우는 모두 다시 요청하게 한다.
export function PasswordNewPage() {
  const router = useRouter()
  const showToast = useToast()
  const tokenRef = useRef('')
  const verifyLink = memberApi.useVerifyPasswordReset()
  const confirmReset = memberApi.useConfirmPasswordReset()
  const {
    register,
    handleSubmit,
    getValues,
    trigger,
    formState: { errors, isValid },
  } = useForm<PasswordNewValues>({
    resolver: zodResolver(passwordNewSchema),
    mode: 'onTouched',
    defaultValues: { password: '', passwordConfirm: '' },
  })
  const { mutate: verify } = verifyLink
  useEffect(() => {
    // 개발 모드에서 효과가 두 번 돌아도 주소에서 지우기 전에 읽은 토큰을 쓴다
    if (!tokenRef.current) tokenRef.current = takeTokenFromHash()
    // 토큰이 없으면 서버가 400으로 답해 만료 안내로 간다
    verify({ data: { token: tokenRef.current } })
  }, [verify])
  // 비밀번호를 고치면 이미 채운 확인 칸도 다시 검사한다. 비어 있으면 "다르다" 오류를 먼저 띄우지 않는다.
  const revalidateConfirm = () => {
    if (getValues('passwordConfirm')) void trigger('passwordConfirm')
  }
  // 확인 요청에 보낸 토큰을 그대로 쓴다
  const token = verifyLink.variables?.data.token ?? ''
  const onSubmit = handleSubmit(({ password }) =>
    confirmReset.mutate(
      { data: { token, password } },
      {
        onSuccess: () => {
          // 서버가 모든 기기의 로그인을 풀었으므로 이 탭도 로그아웃 상태로 바꾼다
          clearAccessToken()
          showToast({ message: '비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요.' })
          router.replace('/login')
        },
      },
    ),
  )

  const linkExpired =
    verifyLink.isError ||
    (confirmReset.error instanceof ApiError &&
      confirmReset.error.code === 'AUTH_RESET_TOKEN_INVALID')
  if (linkExpired) {
    return (
      <>
        <AppHeader type="sub" title="비밀번호 재설정" />
        <main className="flex flex-1 items-center justify-center px-4 py-6">
          <EmptyState
            icon="CircleAlert"
            title="링크를 쓸 수 없어요"
            description="재설정 링크는 30분 동안 한 번만 쓸 수 있어요. 새 링크를 받으면 이전 링크는 쓸 수 없어요. 다시 요청해 주세요."
          />
        </main>
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            <Link href="/password-reset" className={buttonClassName({ size: 'l' })}>
              재설정 링크 다시 받기
            </Link>
          }
        />
      </>
    )
  }
  if (!verifyLink.isSuccess) {
    return (
      <>
        <AppHeader type="sub" title="비밀번호 재설정" />
        <main className="flex flex-1 items-center justify-center">
          <Spinner />
        </main>
      </>
    )
  }

  return (
    <>
      <AppHeader type="sub" title="비밀번호 재설정" />
      <CenterBody>
        <AuthIntro title="새 비밀번호를 정해 주세요" />
        {confirmReset.isError && (
          <Alert tone="danger" title="비밀번호를 바꾸지 못했어요. 잠시 뒤 다시 시도해 주세요." />
        )}
        <TextField
          label="새 비밀번호"
          type="password"
          autoComplete="new-password"
          helper="10자 이상"
          error={errors.password?.message}
          {...register('password', { onChange: revalidateConfirm })}
        />
        <TextField
          label="새 비밀번호 확인"
          type="password"
          autoComplete="new-password"
          error={errors.passwordConfirm?.message}
          {...register('passwordConfirm')}
        />
        <p className="text-caption text-text-tertiary">
          바꾸면 다른 기기에서 로그인된 상태도 모두 풀려요.
        </p>
      </CenterBody>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Button
            size="l"
            disabled={!isValid || confirmReset.isPending}
            loading={confirmReset.isPending}
            onClick={onSubmit}
          >
            비밀번호 바꾸기
          </Button>
        }
      />
    </>
  )
}

// 피그마 V2 · M03-3 이메일 찾기 · 정보 입력
export function FindEmailPage() {
  const router = useRouter()
  const {
    register,
    handleSubmit,
    formState: { errors, isValid },
  } = useForm<FindEmailValues>({
    resolver: zodResolver(findEmailSchema),
    mode: 'onTouched',
    defaultValues: { name: '', birth: '', phone: '' },
  })
  // TODO(api): 이름 · 생년월일 · 휴대폰 번호로 이메일을 찾는다.
  const onSubmit = handleSubmit(() => router.push('/find-email/result'))

  return (
    <>
      <AppHeader type="sub" title="이메일 찾기" />
      <CenterBody>
        <h2 className="text-heading-m">가입할 때 적은 정보를 입력해 주세요</h2>
        <TextField
          label="이름"
          autoComplete="name"
          error={errors.name?.message}
          {...register('name')}
        />
        <BirthDateField label="생년월일" error={errors.birth?.message} {...register('birth')} />
        <PhoneField
          label="휴대폰 번호"
          placeholder="010-1234-5678"
          error={errors.phone?.message}
          {...register('phone')}
        />
        <p className="text-caption text-text-tertiary">
          세 가지가 모두 맞는 계정이 있으면 이메일 일부를 가려서 보여 드려요.
        </p>
      </CenterBody>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Button size="l" disabled={!isValid} onClick={onSubmit}>
            이메일 찾기
          </Button>
        }
      />
    </>
  )
}

// 피그마 V2 · M03-4 이메일 찾기 · 결과
export function FindEmailResultPage() {
  return (
    <>
      <AppHeader type="sub" title="이메일 찾기" />
      <main className="flex flex-1 items-center justify-center px-4 py-6">
        <EmptyState
          icon="CircleCheck"
          title="가입한 이메일을 찾았어요"
          description="ru****@crewrun.kr · 2026.09.12 가입"
          action={
            <Link href="/password-reset" className={buttonClassName({ variant: 'secondary' })}>
              비밀번호 재설정
            </Link>
          }
        />
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Link href="/login" className={buttonClassName({ size: 'l' })}>
            로그인하기
          </Link>
        }
      />
    </>
  )
}
