import { z } from 'zod'

const KEY = 'crewrun:recent-categories'
const MAX = 2

const schema = z.array(z.string())

// 최근 본 종목은 이 브라우저에만 남긴다(서버 API 없음). 저장소를 못 쓰거나 값이 깨져 있으면 빈 목록으로 본다.
export function loadRecentCategories(): string[] {
  try {
    const parsed = schema.safeParse(JSON.parse(localStorage.getItem(KEY) ?? '[]'))
    return parsed.success ? parsed.data : []
  } catch {
    return []
  }
}

/** 같은 종목은 맨 앞으로 옮기고 2개까지만 남긴다 */
export function addRecentCategory(label: string) {
  try {
    const next = [label, ...loadRecentCategories().filter((l) => l !== label)].slice(0, MAX)
    localStorage.setItem(KEY, JSON.stringify(next))
  } catch {
    // 저장소를 못 쓰면 이번에는 남기지 않는다
  }
}
