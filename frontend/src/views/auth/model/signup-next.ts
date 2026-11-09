/**
 * 가입을 마친 뒤 돌아갈 화면(?next=). 로그인 화면에서 회원가입으로 넘어올 때 받는다(BUK-98).
 * 모바일 본인인증은 포트원 창이 페이지를 옮겨 가서 메모리 값이 사라지므로 sessionStorage에 남긴다.
 */
const KEY = 'crewrun:signup-next'

export function saveSignupNext(next: string) {
  try {
    sessionStorage.setItem(KEY, next)
  } catch {
    // 저장소를 쓸 수 없으면 가입 뒤 홈으로 간다
  }
}

/**
 * 가입 첫 화면 주소. 다음 단계에서 입력값이 사라져 첫 화면으로 되돌릴 때 쓴다.
 * 남겨 둔 돌아갈 화면을 ?next=로 붙여야, 첫 화면이 next 없이 열려 그 값을 지우는 일이 없다.
 */
export function signupStartHref(): '/signup' | `/signup?next=${string}` {
  try {
    const next = sessionStorage.getItem(KEY)
    return next ? `/signup?next=${encodeURIComponent(next)}` : '/signup'
  } catch {
    return '/signup'
  }
}

/** 남겨 둔 값을 꺼내고 지운다. 없으면 null */
export function takeSignupNext(): string | null {
  try {
    const next = sessionStorage.getItem(KEY)
    sessionStorage.removeItem(KEY)
    return next
  } catch {
    return null
  }
}
