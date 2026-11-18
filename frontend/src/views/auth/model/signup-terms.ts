// 가입 약관 동의(M04-0) 항목. 필수를 모두 체크해야 다음 단계로 넘어간다.
export const SIGNUP_TERMS = [
  { id: 'service', label: '[필수] 서비스 이용약관', required: true, href: '/terms/service' },
  { id: 'privacy', label: '[필수] 개인정보 수집 · 이용', required: true, href: '/terms/privacy' },
  { id: 'age', label: '[필수] 본인은 만 14세 이상이에요', required: true },
  { id: 'marketing', label: '[선택] 마케팅 정보 받기', required: false, href: '/terms/marketing' },
] as const

/** 시트에서는 「이용약관과 개인정보 동의」 한 줄로 묶어 보이고, 상태·가입 요청은 약관마다 따로 둔다 */
export const BUNDLED_TERM_IDS = ['service', 'privacy'] as const

export type SignupTermId = (typeof SIGNUP_TERMS)[number]['id']
export type SignupTermChecks = Record<SignupTermId, boolean>

export function termChecks(value: boolean): SignupTermChecks {
  return Object.fromEntries(SIGNUP_TERMS.map((t) => [t.id, value])) as SignupTermChecks
}

export function requiredTermsChecked(checks: SignupTermChecks): boolean {
  return SIGNUP_TERMS.every((t) => !t.required || checks[t.id])
}

// 서버 약관 종류(TermsType). "만 14세 이상"(age)도 필수 약관 종류로 보내 동의 기록을 남긴다.
const TERM_TYPES = {
  service: 'SERVICE',
  privacy: 'PRIVACY',
  age: 'AGE_OVER_14',
  marketing: 'MARKETING',
} as const

/** 체크한 약관을 서버 가입 요청의 agreedTerms 값으로 바꾼다. 체크하지 않은 선택 약관은 뺀다. */
export function agreedTermTypes(
  checks: SignupTermChecks,
): (typeof TERM_TYPES)[keyof typeof TERM_TYPES][] {
  return (Object.keys(TERM_TYPES) as (keyof typeof TERM_TYPES)[])
    .filter((id) => checks[id])
    .map((id) => TERM_TYPES[id])
}
