// 날짜만 다루는 값(대회일, 판매 기간 등)을 API와 주고받을 때 쓰는 'YYYY-MM-DD' 변환.
// Date.toISOString()은 UTC로 바꿔서 한국 시간 자정 전후에 하루가 밀리므로, 항상 로컬 날짜 칸을 그대로 읽고 쓴다.

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/

export function toIsoDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** 'YYYY-MM-DD'를 로컬 자정의 Date로 바꾼다. 형식이 틀리거나 없는 날짜(2월 30일 등)면 오류를 던진다. */
export function fromIsoDate(value: string): Date {
  const match = ISO_DATE.exec(value)
  if (!match) {
    throw new Error(`날짜 형식이 아닙니다: ${value}`)
  }
  const [, y, m, d] = match.map(Number)
  const date = new Date(y, m - 1, d)
  if (date.getFullYear() !== y || date.getMonth() !== m - 1 || date.getDate() !== d) {
    throw new Error(`없는 날짜입니다: ${value}`)
  }
  return date
}
