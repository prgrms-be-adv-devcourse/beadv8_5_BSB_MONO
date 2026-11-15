/** 목록·댓글의 짧은 시각: 방금 · N분 전 · N시간 전 · 어제 · M월 D일 */
export function formatRelative(iso: string, now: Date = new Date()): string {
  const diff = now.getTime() - new Date(iso).getTime()
  const minutes = Math.floor(diff / 60_000)
  if (minutes < 1) return '방금'
  if (minutes < 60) return `${minutes}분 전`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}시간 전`
  if (hours < 48) return '어제'
  const date = new Date(iso)
  return `${date.getMonth() + 1}월 ${date.getDate()}일`
}

/** 글 상세의 시각: 11월 9일 21:04 */
export function formatDateTime(iso: string): string {
  const date = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getMonth() + 1}월 ${date.getDate()}일 ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
