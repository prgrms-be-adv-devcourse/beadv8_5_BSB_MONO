const KST_OFFSET_MS = 9 * 60 * 60 * 1000
const MINUTE_MS = 60 * 1000
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/**
 * 백엔드가 주는 시간대 없는 한국 시각(2026-11-03T20:30:00)을 ms로 바꾼다.
 * 서버가 한국 시각(Asia/Seoul)으로 돈다는 약속에 기댄다. 주문 결제 기한 등 다른 화면도 같은 약속이다.
 * 지금 compose.yml은 DB 컨테이너에만 TZ를 지정해서, member-service가 UTC로 돌면 9시간 어긋난다(백엔드에 확인 요청).
 */
export function parseKstDateTime(value: string): number {
  return new Date(`${value}+09:00`).getTime()
}

/** 10/31 (토) 14:20 */
export function formatKstDateTime(value: string): string {
  const kst = new Date(parseKstDateTime(value) + KST_OFFSET_MS)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${kst.getUTCMonth() + 1}/${kst.getUTCDate()} (${WEEKDAYS[kst.getUTCDay()]}) ${pad(kst.getUTCHours())}:${pad(kst.getUTCMinutes())}`
}

/** 남은 시간을 「23시간 12분」처럼 쓴다. 1분이 안 남았으면 「1분」, 이미 지났으면 null. */
export function formatRemaining(expiresAt: string, now: number = Date.now()): string | null {
  const left = parseKstDateTime(expiresAt) - now
  if (left <= 0) return null
  const minutes = Math.max(1, Math.floor(left / MINUTE_MS))
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours === 0) return `${rest}분`
  return rest === 0 ? `${hours}시간` : `${hours}시간 ${rest}분`
}

/** 이 시간 안에 만든 초대는 「방금 만든 링크」로 쓴다. */
const JUST_NOW_MS = 5 * MINUTE_MS

/** 보낸 초대 줄의 제목. 「방금 만든 링크」 또는 「10/29 (목) 09:10에 만든 링크」 */
export function inviteCreatedLabel(createdAt: string, now: number = Date.now()): string {
  if (now - parseKstDateTime(createdAt) < JUST_NOW_MS) return '방금 만든 링크'
  return `${formatKstDateTime(createdAt)}에 만든 링크`
}
