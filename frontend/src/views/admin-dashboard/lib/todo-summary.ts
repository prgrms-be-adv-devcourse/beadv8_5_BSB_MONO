export type TodoSummary =
  { kind: 'link' } | { kind: 'none' } | { kind: 'count'; count: number; oldest?: string }

/** count가 null(셀 수 없음)이거나 undefined(아직 못 불러옴)면 숫자 없이 링크만 보여 준다 */
export function todoSummary(count: number | null | undefined, oldestAt?: string): TodoSummary {
  if (count == null) return { kind: 'link' }
  if (count === 0) return { kind: 'none' }
  return { kind: 'count', count, oldest: oldestAt ? formatMonthDay(oldestAt) : undefined }
}

/** ISO 시각 중 가장 이른 것 */
export function oldestDay(times: string[]): string | undefined {
  return [...times].sort()[0]
}

function formatMonthDay(iso: string) {
  const d = new Date(iso)
  return `${d.getMonth() + 1}/${d.getDate()}`
}
