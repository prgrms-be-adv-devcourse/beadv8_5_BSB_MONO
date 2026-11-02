import type { StatsPeriod } from './period'

// 판매자 대회 등록 화면에서 입력 오류가 난 항목 묶음 순위 (명세 S-25).
// GTM은 한 번의 오류에 걸린 묶음을 「기본 정보,이미지」처럼 쉼표로 이어 error_sections 값으로 보낸다.

export type FormErrorGroup = { name: string; users: number }

export type FormErrorStats = { period: StatsPeriod; groups: FormErrorGroup[] }

/** 보여 줄 순위 수 */
export const FORM_ERROR_TOP = 3

/**
 * 묶음 조합별 사람 수를 묶음 하나씩으로 나눠 더한다.
 * 한 사람이 서로 다른 조합으로 여러 번 막혔으면 같은 묶음에 두 번 셀 수 있어 정확한 사람 수보다 조금 많을 수 있다.
 */
export function rankFormErrors(rows: { value: string; users: number }[]): FormErrorGroup[] {
  const users = new Map<string, number>()
  for (const row of rows) {
    for (const name of new Set(row.value.split(',').map((s) => s.trim()))) {
      if (name && name !== '(not set)') users.set(name, (users.get(name) ?? 0) + row.users)
    }
  }
  return [...users]
    .map(([name, count]) => ({ name, users: count }))
    .sort((a, b) => b.users - a.users || a.name.localeCompare(b.name, 'ko'))
    .slice(0, FORM_ERROR_TOP)
}
