import type { SignupTermChecks } from './signup-terms'

/**
 * 모바일 본인인증은 포트원 창이 페이지를 옮겨 가서, 메모리에 있던 가입 단계 입력값이 사라진다.
 * 창을 열기 전에 약관 동의와 이메일 · 닉네임만 sessionStorage에 남기고, 돌아오면 되살린 뒤 지운다.
 * 비밀번호는 남기지 않는다(이메일 가입은 비밀번호를 넣기 전에 본인인증을 한다).
 */
export type SignupDraft = {
  terms: SignupTermChecks
  termsAgreed: boolean
  email?: string
  nickname?: string
}

const KEY = 'crewrun:signup-draft'

export function saveSignupDraft(draft: SignupDraft) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(draft))
  } catch {
    // 저장소를 쓸 수 없으면(사생활 보호 모드 등) 돌아와서 약관부터 다시 한다
  }
}

/** 남겨 둔 입력값을 꺼내고 지운다. 없으면 null */
export function takeSignupDraft(): SignupDraft | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    sessionStorage.removeItem(KEY)
    return raw ? (JSON.parse(raw) as SignupDraft) : null
  } catch {
    return null
  }
}
