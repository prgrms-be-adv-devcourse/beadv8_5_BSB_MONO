import { TextField } from '@/shared/ui'

import type { VerifiedIdentity } from '../api/identity-verification'

export type LockedIdentityFieldsProps = {
  /** 본인인증 결과. 없으면 인증 전이라 칸을 비우고 「본인인증을 하면 채워져요」를 보여 준다 */
  identity: VerifiedIdentity | null
  /** 인증 전에 이름 칸에 미리 보여 줄 소셜 제공자의 이름과 제공자 이름(카카오 · 구글) */
  provider?: { name?: string; label: string }
}

/**
 * 본인인증으로 확인한 이름 · 생년월일 · 휴대폰 번호 (시안 M04-P 4·5·6).
 * 가입은 서버가 확인한 값을 쓰므로 화면에서는 고칠 수 없게 잠가서 보여 준다.
 */
export function LockedIdentityFields({ identity, provider }: LockedIdentityFieldsProps) {
  const pending = '본인인증을 하면 채워져요'
  const nameHelper = identity
    ? '본인인증으로 확인한 이름이라 바꿀 수 없어요'
    : provider?.name
      ? `${provider.label}에서 받은 이름이에요. 본인인증을 하면 인증된 이름으로 바뀌어요`
      : pending
  const lockedHelper = identity ? '본인인증으로 확인한 정보라 바꿀 수 없어요' : pending
  return (
    <>
      <TextField
        label="이름"
        value={identity?.name ?? provider?.name ?? ''}
        disabled
        readOnly
        helper={nameHelper}
      />
      <TextField
        label="생년월일"
        // 시안처럼 1995.03.02로 보여 준다 (값은 1995-03-02)
        value={identity?.birthDate?.replaceAll('-', '.') ?? ''}
        disabled
        readOnly
        helper={lockedHelper}
      />
      <TextField
        label="휴대폰 번호"
        value={identity?.phone ?? ''}
        disabled
        readOnly
        helper={lockedHelper}
      />
    </>
  )
}
