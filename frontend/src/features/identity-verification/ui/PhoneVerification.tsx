'use client'

import { useState } from 'react'

import { MOCK_VERIFICATION_CODE, type memberModel } from '@/shared/api'
import { Button, Icon, TextField } from '@/shared/ui'

import { usePhoneVerification } from '../model/use-phone-verification'

const PHONE_PATTERN = /^01[016789]-?\d{3,4}-?\d{4}$/
const CODE_LENGTH = 6

/** 남은 초를 "2분 59초"처럼 읽기 쉽게 쓴다 */
function formatRemaining(seconds: number) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return m > 0 ? `${m}분 ${String(s).padStart(2, '0')}초` : `${s}초`
}

export type PhoneVerificationProps = {
  /** 휴대폰 번호 칸의 지금 값 */
  phone: string
  /** 이 번호로 인증을 마쳤는지 */
  verified: boolean
  onVerified: (result: memberModel.IdentityVerificationDto) => void
  /** 인증한 번호를 풀고 다른 번호로 다시 인증한다. 없으면 버튼을 보여 주지 않는다 */
  onReset?: () => void
}

/**
 * 휴대폰 번호 칸 아래에 붙는 인증 영역. 인증번호 받기 → 6자리 입력 → 확인.
 * 가입 폼 안에 있으므로 인증번호 칸에서 Enter를 누르면 가입 대신 인증번호를 확인한다.
 * 목업이라 문자가 가지 않으므로 인증번호를 화면에 알려 준다.
 */
export function PhoneVerification({
  phone,
  verified,
  onVerified,
  onReset,
}: PhoneVerificationProps) {
  const [code, setCode] = useState('')
  const verification = usePhoneVerification({
    phone,
    onVerified: (result) => {
      setCode('')
      onVerified(result)
    },
  })

  if (verified) {
    return (
      <div className="flex items-center justify-between gap-2">
        <p role="status" className="flex items-center gap-1.5 text-caption text-text-success">
          <Icon name="CircleCheck" className="size-4" />
          휴대폰 인증을 마쳤어요
        </p>
        {onReset && (
          <Button variant="ghost" size="s" onClick={onReset}>
            번호 바꾸기
          </Button>
        )}
      </div>
    )
  }

  const phoneValid = PHONE_PATTERN.test(phone.trim())
  const send = () => {
    setCode('')
    void verification.send()
  }

  if (!verification.sent) {
    return (
      <div className="flex flex-col gap-1.5">
        <Button
          variant="secondary"
          disabled={!phoneValid}
          loading={verification.sending}
          onClick={send}
        >
          인증번호 받기
        </Button>
        {verification.error && (
          <p role="alert" className="text-caption text-text-danger">
            {verification.error}
          </p>
        )}
      </div>
    )
  }

  const canConfirm = code.length === CODE_LENGTH && !verification.expired
  const confirm = () => {
    if (canConfirm) void verification.confirm(code)
  }
  return (
    <div className="flex flex-col gap-3 rounded-md bg-bg-subtle p-4">
      <p className="text-body-s text-text-secondary">
        체험용 화면이라 문자가 가지 않아요. 인증번호 {MOCK_VERIFICATION_CODE}을 입력해 주세요.
      </p>
      <TextField
        label="인증번호"
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={CODE_LENGTH}
        placeholder="6자리 숫자"
        value={code}
        onChange={(event) => setCode(event.currentTarget.value.replace(/\D/g, ''))}
        onKeyDown={(event) => {
          if (event.key !== 'Enter') return
          event.preventDefault()
          confirm()
        }}
        helper={
          verification.expired
            ? undefined
            : `남은 시간 ${formatRemaining(verification.remainingSeconds)}`
        }
        error={verification.error}
      />
      <div className="flex gap-2 *:flex-1">
        <Button variant="ghost" loading={verification.sending} onClick={send}>
          인증번호 다시 받기
        </Button>
        <Button disabled={!canConfirm} loading={verification.confirming} onClick={confirm}>
          인증번호 확인하기
        </Button>
      </div>
    </div>
  )
}
