'use client'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'

type Values = Record<string, string>

/**
 * 목록 상태(탭 · 필터 · 정렬 · 페이지)를 URL 쿼리로 들고 있는다. 뒤로 가기·새로고침·링크 공유에도 값이 남는다.
 * - 기본값과 같은 값은 URL에서 지운다.
 * - allowed에 적은 키는 목록 밖의 값이면 기본값으로 읽는다.
 * - 쓸 때는 history를 쌓지 않고(replace) 스크롤도 유지한다.
 */
export function useSearchParamsState<T extends Values>(
  defaults: T,
  allowed: { [K in keyof T]?: readonly string[] } = {},
): [T, (patch: Partial<T>) => void] {
  const router = useRouter()
  const pathname = usePathname()
  const params = useSearchParams()

  const values = { ...defaults }
  for (const key of Object.keys(defaults) as (keyof T & string)[]) {
    const raw = params.get(key)
    if (raw !== null && (allowed[key] === undefined || allowed[key].includes(raw))) {
      values[key] = raw as T[typeof key]
    }
  }

  function update(patch: Partial<T>) {
    const next = new URLSearchParams(params.toString())
    for (const [key, value] of Object.entries(patch)) {
      if (value === undefined || value === defaults[key]) next.delete(key)
      else next.set(key, value as string)
    }
    const qs = next.toString()
    // typedRoutes는 쿼리가 붙은 문자열을 모른다
    router.replace((qs ? `${pathname}?${qs}` : pathname) as never, { scroll: false })
  }

  return [values, update]
}
