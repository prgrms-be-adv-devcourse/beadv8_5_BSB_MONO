'use client'

import type { FieldValues, Path, PathValue, UseFormSetValue } from 'react-hook-form'

import type { memberModel } from '@/shared/api'

import type { SignupProfileValues } from './schemas'
import { useSignupStep } from './signup-store'

type WithProfile = FieldValues & SignupProfileValues

/**
 * 회원 정보 단계의 본인 확인. 이메일 가입과 소셜 가입이 함께 쓴다.
 * - 휴대폰 인증: 인증한 번호를 잠근다.
 * - PASS 본인인증: 확인된 이름 · 생년월일 · 휴대폰 번호로 칸을 채우고 잠근다.
 * 결과는 가입 단계 스토어에 남아, 계정 단계에 다녀와도 인증을 다시 하지 않는다.
 */
export function useSignupVerification<T extends WithProfile>(setValue: UseFormSetValue<T>) {
  const verification = useSignupStep((s) => s.verification)
  const setVerification = useSignupStep((s) => s.setVerification)

  const fill = (name: keyof SignupProfileValues, value: string) =>
    setValue(name as Path<T>, value as PathValue<T, Path<T>>, {
      shouldValidate: true,
      shouldDirty: true,
    })

  const onVerified = (result: memberModel.IdentityVerificationDto) => {
    fill('phone', result.phone)
    if (result.method === 'PASS') {
      if (result.name) fill('name', result.name)
      if (result.birthDate) fill('birth', result.birthDate)
    }
    setVerification(result)
  }

  return {
    verification,
    onVerified,
    /** 잠근 칸을 풀고 인증을 처음부터 다시 한다. 채운 값은 그대로 두어 고치기만 하면 된다 */
    reset: () => setVerification(null),
  }
}
