'use client'

import { Alert, Badge, Button } from '@/shared/ui'

import type { VerifiedIdentity } from '../api/identity-verification'
import {
  useIdentityVerification,
  type UseIdentityVerificationOptions,
} from '../model/use-identity-verification'

export type IdentityVerificationBandProps = UseIdentityVerificationOptions & {
  verification: VerifiedIdentity | null
}

/**
 * 소셜 가입 회원 정보 단계의 「본인 확인」 띠 (시안 M04-P 5·6).
 * 띠 안의 「휴대폰 본인인증」 버튼으로 시작하고, 인증을 마치면 버튼 자리에 「본인인증 완료」 배지를 보여 준다.
 */
export function IdentityVerificationBand({
  verification,
  ...options
}: IdentityVerificationBandProps) {
  const { start, opening, mock, element } = useIdentityVerification(options)
  return (
    <section aria-labelledby="identity-band-title" className="flex flex-col gap-3">
      <div className="flex flex-col items-start gap-2">
        <h2 id="identity-band-title" className="text-title3 text-text-primary">
          본인 확인
        </h2>
        {verification && <Badge tone="success">본인인증 완료</Badge>}
      </div>
      {!verification && (
        <Button variant="secondary" size="l" loading={opening} onClick={start}>
          휴대폰 본인인증
        </Button>
      )}
      {mock && <IdentityMockNotice />}
      {element}
    </section>
  )
}

/** 가짜 응답 모드에서만 보여 주는 체험용 표시 */
export function IdentityMockNotice() {
  return <Alert tone="warning" title="체험용 화면이에요. 실제 인증은 하지 않아요" />
}
