'use client'

import { createStore } from 'zustand/vanilla'

import type { memberModel } from '@/shared/api'
import { createStoreContext } from '@/shared/lib'

import type { SignupAccountValues, SignupProfileValues } from './schemas'
import {
  requiredTermsChecked,
  termChecks,
  type SignupTermChecks,
  type SignupTermId,
} from './signup-terms'

// 회원가입 단계(약관 → 계정 → 회원 정보) 입력값. 단계를 오가도 값이 남도록 (auth) 레이아웃에서 Provider를 둔다.
// UI 상태만 둔다. 가입 요청과 응답은 TanStack Query로 다룬다.
type SignupStepState = {
  terms: SignupTermChecks
  /** 약관 바텀시트에서 "동의하고 계속하기"를 눌렀는지 */
  termsAgreed: boolean
  account: SignupAccountValues
  profile: SignupProfileValues
  /** 휴대폰 인증 · PASS 본인인증 결과. 마쳐야 가입할 수 있다. 회원 정보 단계를 떠났다 와도 남는다 */
  verification: memberModel.IdentityVerificationDto | null
  setTerm: (id: SignupTermId, checked: boolean) => void
  setAllTerms: (checked: boolean) => void
  /** 필수 약관을 모두 체크했을 때만 동의로 남기고 결과를 돌려준다. */
  agreeTerms: () => boolean
  setAccount: (account: SignupAccountValues) => void
  setProfile: (profile: SignupProfileValues) => void
  setVerification: (verification: memberModel.IdentityVerificationDto | null) => void
}

/** 테스트·시안 미리보기에서 단계 중간부터 시작할 때 넘기는 값 */
export type SignupStepInitial = Partial<
  Pick<SignupStepState, 'terms' | 'termsAgreed' | 'account' | 'profile' | 'verification'>
>

export const { Provider: SignupStepProvider, useStore: useSignupStep } = createStoreContext(
  'SignupStep',
  (initial: SignupStepInitial = {}) =>
    createStore<SignupStepState>()((set, get) => ({
      terms: termChecks(false),
      termsAgreed: false,
      account: { email: '', password: '', passwordConfirm: '' },
      profile: { name: '', birth: '', phone: '', nickname: '' },
      verification: null,
      ...initial,
      setTerm: (id, checked) =>
        set((s) => {
          const terms = { ...s.terms, [id]: checked }
          return { terms, termsAgreed: s.termsAgreed && requiredTermsChecked(terms) }
        }),
      setAllTerms: (checked) =>
        set((s) => ({ terms: termChecks(checked), termsAgreed: s.termsAgreed && checked })),
      agreeTerms: () => {
        const agreed = requiredTermsChecked(get().terms)
        set({ termsAgreed: agreed })
        return agreed
      },
      setAccount: (account) => set({ account }),
      setProfile: (profile) => set({ profile }),
      setVerification: (verification) => set({ verification }),
    })),
)
