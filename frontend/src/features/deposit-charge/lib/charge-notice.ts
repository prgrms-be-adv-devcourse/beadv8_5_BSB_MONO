/**
 * 충전 결과 화면이 충전 화면(M21)으로 돌아가며 남기는 결과. 돌아갈 때 주소를 바꾸지 않고 기록을 거슬러 가므로 sessionStorage로 넘긴다.
 * - completed: 충전됨. 충전 화면은 바로 앞 화면(보통 예치금)으로 다시 돌아간다
 * - failed: 충전 안 됨(돈은 빠지지 않음). message가 있으면 서버가 알려 준 거절 사유
 */
export type ChargeNotice = { kind: 'completed' } | { kind: 'failed'; message?: string }

const KEY = 'crewrun:charge-notice'

/** 돌아가는 데는 몇 초면 된다. 이보다 오래된 결과는 엉뚱한 곳에 도착해 남은 것이라 버린다 */
const NOTICE_TTL_MS = 60_000

export function saveChargeNotice(notice: ChargeNotice, now = Date.now()) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ ...notice, savedAt: now }))
  } catch {
    // 저장이 막혀 있으면 충전 화면이 결과를 알리지 못할 뿐 충전 자체는 끝났다
  }
}

/** 한 번만 읽는다. 읽으면 지워서 다시 열 때 또 보이지 않게 한다 */
export function takeChargeNotice(now = Date.now()): ChargeNotice | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    sessionStorage.removeItem(KEY)
    const parsed: unknown = raw === null ? null : JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null || !('kind' in parsed)) return null
    const savedAt = 'savedAt' in parsed && typeof parsed.savedAt === 'number' ? parsed.savedAt : 0
    if (now - savedAt > NOTICE_TTL_MS) return null
    if (parsed.kind === 'completed') return { kind: 'completed' }
    if (parsed.kind !== 'failed') return null
    const message =
      'message' in parsed && typeof parsed.message === 'string' ? parsed.message : undefined
    return { kind: 'failed', message }
  } catch {
    return null
  }
}
