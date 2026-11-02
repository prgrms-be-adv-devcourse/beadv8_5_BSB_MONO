'use client'

import { useEffect, useState } from 'react'

import { ApiError, type memberModel } from '@/shared/api'

import { confirmPhoneCode, requestPhoneCode } from '../api/identity-verification'

// 화면 문구. 서버 문구(합니다체) 대신 화면 말투(해요체)로 보여 준다.
export const PHONE_VERIFICATION_MESSAGES = {
  sendFailed: '인증번호를 보내지 못했어요. 잠시 후 다시 시도해 주세요.',
  codeMismatch: '인증번호가 맞지 않아요. 다시 확인해 주세요.',
  expired: '인증 시간이 지났어요. 인증번호를 다시 받아 주세요.',
  confirmFailed: '인증번호를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.',
} as const

const digits = (phone: string) => phone.replace(/\D/g, '')

type Sent = { verificationId: string; phone: string; expiresAt: number }

/**
 * 휴대폰 번호 인증 상태. 인증번호 받기 → 6자리 확인 순서다.
 * 보낸 뒤 번호를 고치면 보낸 기록을 버린다(다른 번호로 받은 인증번호는 쓸 수 없다).
 */
export function usePhoneVerification({
  phone,
  onVerified,
}: {
  phone: string
  onVerified: (result: memberModel.IdentityVerificationDto) => void
}) {
  const [sent, setSent] = useState<Sent | null>(null)
  const [sending, setSending] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string>()
  const [now, setNow] = useState(() => Date.now())

  const active = sent !== null && digits(sent.phone) === digits(phone) ? sent : null
  const remainingSeconds = active ? Math.max(0, Math.ceil((active.expiresAt - now) / 1000)) : 0
  const expired = active !== null && remainingSeconds === 0

  // 남은 시간을 1초마다 줄인다. 시간이 지나면 멈춘다.
  useEffect(() => {
    if (!active || expired) return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [active, expired])

  const send = async () => {
    setError(undefined)
    setSending(true)
    try {
      const result = await requestPhoneCode(phone.trim())
      const at = Date.now()
      setNow(at)
      setSent({
        verificationId: result.verificationId,
        phone: phone.trim(),
        expiresAt: at + result.expiresInSeconds * 1000,
      })
    } catch {
      setError(PHONE_VERIFICATION_MESSAGES.sendFailed)
    } finally {
      setSending(false)
    }
  }

  const confirm = async (code: string) => {
    if (!active) return
    if (expired) {
      setError(PHONE_VERIFICATION_MESSAGES.expired)
      return
    }
    setError(undefined)
    setConfirming(true)
    try {
      onVerified(await confirmPhoneCode(active.verificationId, code))
      setSent(null)
    } catch (caught) {
      const errorCode = caught instanceof ApiError ? caught.code : undefined
      setError(
        errorCode === 'AUTH_VERIFICATION_CODE_MISMATCH'
          ? PHONE_VERIFICATION_MESSAGES.codeMismatch
          : errorCode === 'AUTH_VERIFICATION_EXPIRED'
            ? PHONE_VERIFICATION_MESSAGES.expired
            : PHONE_VERIFICATION_MESSAGES.confirmFailed,
      )
    } finally {
      setConfirming(false)
    }
  }

  return {
    /** 지금 번호로 받은 인증번호가 있다 */
    sent: active !== null,
    remainingSeconds,
    expired,
    sending,
    confirming,
    error: error ?? (expired ? PHONE_VERIFICATION_MESSAGES.expired : undefined),
    send,
    confirm,
  }
}
